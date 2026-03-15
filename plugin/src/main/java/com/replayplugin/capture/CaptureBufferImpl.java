package com.replayplugin.capture;

import com.replayplugin.config.PluginConfig;
import com.replayplugin.config.TriggerConfig;
import com.replayplugin.job.RenderQueue;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.ChunkSnapshot;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Per-player rolling ring buffer of WorldSnapshots. Capture on main thread; on trigger
 * freeze pre-frames, schedule post-frame capture, then produce RenderJob.
 */
public final class CaptureBufferImpl implements CaptureBuffer {

    private static final Set<String> POSE_NAMES = Set.of("STANDING", "SNEAKING", "SWIMMING", "SLEEPING", "FALL_FLYING");
    private static final String POSE_DEFAULT = "STANDING";

    private final Plugin plugin;
    private final PluginConfig config;
    private final RenderQueue renderQueue;
    private final ConcurrentHashMap<UUID, ArrayDeque<FrameSnapshot>> buffers = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, PostCapture> postCaptures = new ConcurrentHashMap<>();
    private final int captureRateTicks;
    private final int maxPreFrames;
    private final int radiusChunks;
    private BukkitTask tickTask;

    public CaptureBufferImpl(Plugin plugin, PluginConfig config, RenderQueue renderQueue) {
        this.plugin = plugin;
        this.config = config;
        this.renderQueue = renderQueue;
        int minRate = Integer.MAX_VALUE;
        int maxPre = 0;
        int maxRadius = 0;
        for (TriggerConfig t : config.getTriggers().values()) {
            int rate = Math.max(1, t.getCaptureRateTicks());
            minRate = Math.min(minRate, rate);
            int preFrames = t.getPreSeconds() * (20 / rate);
            maxPre = Math.max(maxPre, preFrames);
            maxRadius = Math.max(maxRadius, t.getRadiusChunks());
        }
        this.captureRateTicks = minRate == Integer.MAX_VALUE ? 2 : minRate;
        this.maxPreFrames = maxPre == 0 ? 50 : maxPre;
        this.radiusChunks = maxRadius == 0 ? 5 : maxRadius;
    }

    /**
     * Start the repeating capture task. Call from plugin onEnable.
     */
    public void startCaptureTask() {
        if (tickTask != null) return;
        tickTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tickAll, captureRateTicks, captureRateTicks);
    }

    /**
     * Stop the capture task. Call from plugin onDisable.
     */
    public void stopCaptureTask() {
        if (tickTask != null) {
            tickTask.cancel();
            tickTask = null;
        }
    }

    private void tickAll() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            onTick(p);
        }
    }

    @Override
    public void onTick(Player player) {
        if (player == null || !player.isOnline()) return;
        UUID uuid = player.getUniqueId();
        if (postCaptures.containsKey(uuid)) return;
        int radius = radiusChunks;
        Location loc = player.getLocation();
        int cx = loc.getChunk().getX();
        int cz = loc.getChunk().getZ();
        List<int[]> chunkCoords = new ArrayList<>();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                chunkCoords.add(new int[]{cx + dx, cz + dz});
            }
        }
        org.bukkit.World world = player.getWorld();
        List<Chunk> chunks = new ArrayList<>(chunkCoords.size());
        List<CompletableFuture<Chunk>> futures = new ArrayList<>();
        for (int[] c : chunkCoords) {
            futures.add(world.getChunkAtAsync(c[0], c[1]));
        }
        CompletableFuture.allOf(futures.stream().map(f -> (CompletableFuture<?>) f).toArray(CompletableFuture[]::new))
                .thenAccept(v -> {
                    for (int i = 0; i < chunkCoords.size(); i++) {
                        try {
                            Chunk ch = futures.get(i).get();
                            if (ch != null && ch.isLoaded()) chunks.add(ch);
                        } catch (Exception ignored) {}
                    }
                    if (chunks.size() != chunkCoords.size()) {
                        Bukkit.getScheduler().runTaskLater(plugin, () -> retryTick(player, chunkCoords, world), 1L);
                        return;
                    }
                    Bukkit.getScheduler().runTask(plugin, () -> captureAndPush(player, chunks));
                })
                .exceptionally(t -> null);
    }

    private void retryTick(Player player, List<int[]> chunkCoords, org.bukkit.World world) {
        if (!player.isOnline()) return;
        List<Chunk> chunks = new ArrayList<>();
        for (int[] c : chunkCoords) {
            Chunk ch = world.getChunkAt(c[0], c[1]);
            if (ch.isLoaded()) chunks.add(ch);
        }
        if (chunks.size() != chunkCoords.size()) {
            plugin.getLogger().warning("ReplayPlugin: Chunk(s) not loaded for player " + player.getName() + "; skipping capture tick.");
            return;
        }
        captureAndPush(player, chunks);
    }

    private void captureAndPush(Player player, List<Chunk> chunks) {
        if (!player.isOnline()) return;
        long tick = Bukkit.getCurrentTick();
        EntityState entityState = captureEntityState(player);
        List<ChunkData> chunkDataList = new ArrayList<>();
        int minY = chunks.isEmpty() ? -64 : chunks.get(0).getWorld().getMinHeight();
        int maxY = chunks.isEmpty() ? 320 : chunks.get(0).getWorld().getMaxHeight();
        for (Chunk ch : chunks) {
            ChunkSnapshot snap = ch.getChunkSnapshot(true, true, true);
            ChunkData cd = snapshotToChunkData(snap, minY, maxY);
            if (cd != null) chunkDataList.add(cd);
        }
        if (chunkDataList.size() != chunks.size()) return;
        FrameSnapshot frame = new FrameSnapshot(-1, tick, entityState, chunkDataList);
        buffers.compute(player.getUniqueId(), (u, deque) -> {
            ArrayDeque<FrameSnapshot> q = deque != null ? deque : new ArrayDeque<>();
            q.addLast(frame);
            while (q.size() > maxPreFrames) q.removeFirst();
            return q;
        });
    }

    private EntityState captureEntityState(Player player) {
        Location loc = player.getLocation();
        String pose = toPoseString(player.getPose());
        Map<String, String> equipment = new LinkedHashMap<>();
        equipment.put("main_hand", itemId(nullableType(player.getEquipment().getItemInMainHand())));
        equipment.put("off_hand", itemId(nullableType(player.getEquipment().getItemInOffHand())));
        equipment.put("head", itemId(nullableType(player.getEquipment().getHelmet())));
        equipment.put("chest", itemId(nullableType(player.getEquipment().getChestplate())));
        equipment.put("legs", itemId(nullableType(player.getEquipment().getLeggings())));
        equipment.put("feet", itemId(nullableType(player.getEquipment().getBoots())));
        String skinUrl = "";
        try {
            if (player.getPlayerProfile().getTextures().getSkin() != null) {
                skinUrl = player.getPlayerProfile().getTextures().getSkin().toString();
            }
        } catch (Exception ignored) {}
        return new EntityState(
                loc.getX(), loc.getY(), loc.getZ(),
                loc.getYaw(), loc.getPitch(),
                pose, equipment, skinUrl);
    }

    private static Material nullableType(ItemStack stack) {
        return stack == null ? Material.AIR : stack.getType();
    }

    private static String itemId(Material m) {
        if (m == null || m == Material.AIR) return "minecraft:air";
        return m.getKey().toString();
    }

    private static String toPoseString(org.bukkit.entity.Pose pose) {
        if (pose == null) return POSE_DEFAULT;
        String name = pose.name();
        return POSE_NAMES.contains(name) ? name : POSE_DEFAULT;
    }

    private ChunkData snapshotToChunkData(ChunkSnapshot snap, int minY, int maxY) {
        int cx = snap.getX();
        int cz = snap.getZ();
        List<BlockEntry> blocks = new ArrayList<>();
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                for (int y = minY; y < maxY; y++) {
                    BlockData data = snap.getBlockData(x, y, z);
                    Material type = data.getMaterial();
                    if (type == Material.AIR && snap.getBlockSkyLight(x, y, z) == 15 && snap.getBlockEmittedLight(x, y, z) == 0) continue;
                    int wx = cx * 16 + x;
                    int wz = cz * 16 + z;
                    String blockId = type.getKey().toString();
                    Map<String, String> props = parseBlockStateProperties(data);
                    int sky = snap.getBlockSkyLight(x, y, z);
                    int block = snap.getBlockEmittedLight(x, y, z);
                    blocks.add(new BlockEntry(wx, y, wz, blockId, props, sky, block));
                }
            }
        }
        String biome = "minecraft:plains";
        try {
            biome = snap.getBiome(8, minY + (maxY - minY) / 2, 8).getKey().toString();
        } catch (Exception ignored) {}
        return new ChunkData(cx, cz, biome, blocks);
    }

    private static Map<String, String> parseBlockStateProperties(BlockData data) {
        String s = data.getAsString();
        int bracket = s.indexOf('[');
        if (bracket < 0) return Map.of();
        String inner = s.substring(bracket + 1, s.endsWith("]") ? s.length() - 1 : s.length());
        if (inner.isEmpty()) return Map.of();
        Map<String, String> out = new LinkedHashMap<>();
        for (String part : inner.split(",")) {
            int eq = part.indexOf('=');
            if (eq > 0) out.put(part.substring(0, eq).trim(), part.substring(eq + 1).trim());
        }
        return out;
    }

    @Override
    public RenderJob onTrigger(Player player, TriggerConfig triggerConfig) {
        if (player == null || !player.isOnline()) return null;
        UUID uuid = player.getUniqueId();
        ArrayDeque<FrameSnapshot> deque = buffers.get(uuid);
        if (deque == null || deque.isEmpty()) return null;

        List<FrameSnapshot> preFrames = new ArrayList<>(deque);
        int rate = Math.max(1, triggerConfig.getCaptureRateTicks());
        int preSeconds = triggerConfig.getPreSeconds();
        int preCount = preSeconds * (20 / rate);
        if (preFrames.size() > preCount) {
            preFrames = preFrames.subList(preFrames.size() - preCount, preFrames.size());
        }
        for (int i = 0; i < preFrames.size(); i++) {
            FrameSnapshot f = preFrames.get(i);
            preFrames.set(i, new FrameSnapshot(i, f.getCapturedAtTick(), f.getEntityState(), f.getChunks()));
        }
        final List<FrameSnapshot> preFramesFinal = preFrames;

        int postFramesCount = triggerConfig.getPostSeconds() * (20 / rate);
        List<FrameSnapshot> postFrames = new ArrayList<>();
        AtomicInteger postRemaining = new AtomicInteger(postFramesCount);
        String eventType = triggerConfig.getEvent();
        String playerName = player.getName();
        int radius = triggerConfig.getRadiusChunks();

        Runnable finishJob = () -> {
            RenderConfigDto renderConfig = buildRenderConfig(triggerConfig);
            java.time.LocalDateTime now = java.time.LocalDateTime.now();
            String timestamp = now.format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
            RenderJob job = new RenderJob(uuid, playerName, eventType, timestamp, renderConfig, preFramesFinal, new ArrayList<>(postFrames));
            postCaptures.remove(uuid);
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (renderQueue != null) renderQueue.enqueue(job);
            });
        };

        if (postFramesCount == 0) {
            RenderConfigDto renderConfig = buildRenderConfig(triggerConfig);
            String timestamp = java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
            return new RenderJob(uuid, playerName, eventType, timestamp, renderConfig, preFrames, List.of());
        }

        PostCapture pc = new PostCapture(player, triggerConfig, preFrames, postFrames, postRemaining, finishJob, radius);
        postCaptures.put(uuid, pc);
        schedulePostCapture(pc);
        return null;
    }

    private void schedulePostCapture(PostCapture pc) {
        if (!pc.player.isOnline()) {
            pc.finishJob.run();
            return;
        }
        int radius = pc.radiusChunks;
        Player player = pc.player;
        int cx = player.getLocation().getChunk().getX();
        int cz = player.getLocation().getChunk().getZ();
        List<int[]> coords = new ArrayList<>();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                coords.add(new int[]{cx + dx, cz + dz});
            }
        }
        org.bukkit.World world = player.getWorld();
        List<CompletableFuture<Chunk>> futures = coords.stream()
                .map(c -> world.getChunkAtAsync(c[0], c[1]))
                .collect(Collectors.toList());
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .thenAccept(v -> {
                    List<Chunk> chunks = new ArrayList<>();
                    for (int i = 0; i < coords.size(); i++) {
                        try {
                            Chunk ch = futures.get(i).get();
                            if (ch != null && ch.isLoaded()) chunks.add(ch);
                        } catch (Exception ignored) {}
                    }
                    if (chunks.size() != coords.size()) {
                        Bukkit.getScheduler().runTaskLater(plugin, () -> retryPostCapture(pc), 1L);
                        return;
                    }
                    Bukkit.getScheduler().runTask(plugin, () -> doPostCapture(pc, chunks));
                })
                .exceptionally(t -> null);
    }

    private void retryPostCapture(PostCapture pc) {
        if (!pc.player.isOnline()) {
            pc.finishJob.run();
            return;
        }
        int radius = pc.radiusChunks;
        org.bukkit.World world = pc.player.getWorld();
        int cx = pc.player.getLocation().getChunk().getX();
        int cz = pc.player.getLocation().getChunk().getZ();
        List<Chunk> chunks = new ArrayList<>();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                Chunk ch = world.getChunkAt(cx + dx, cz + dz);
                if (ch.isLoaded()) chunks.add(ch);
            }
        }
        int expected = (2 * radius + 1) * (2 * radius + 1);
        if (chunks.size() != expected) {
            plugin.getLogger().warning("ReplayPlugin: Chunk(s) not loaded for post-capture; submitting truncated job for " + pc.player.getName());
            pc.finishJob.run();
            return;
        }
        doPostCapture(pc, chunks);
    }

    private void doPostCapture(PostCapture pc, List<Chunk> chunks) {
        if (!pc.player.isOnline()) {
            pc.finishJob.run();
            return;
        }
        long tick = Bukkit.getCurrentTick();
        EntityState entityState = captureEntityState(pc.player);
        List<ChunkData> chunkDataList = new ArrayList<>();
        int minY = chunks.isEmpty() ? -64 : chunks.get(0).getWorld().getMinHeight();
        int maxY = chunks.isEmpty() ? 320 : chunks.get(0).getWorld().getMaxHeight();
        for (Chunk ch : chunks) {
            ChunkSnapshot snap = ch.getChunkSnapshot(true, true, true);
            ChunkData cd = snapshotToChunkData(snap, minY, maxY);
            if (cd != null) chunkDataList.add(cd);
        }
        if (chunkDataList.size() != chunks.size()) {
            pc.finishJob.run();
            return;
        }
        int frameIndex = pc.preFrames.size() + pc.postFrames.size();
        pc.postFrames.add(new FrameSnapshot(frameIndex, tick, entityState, chunkDataList));
        if (pc.postRemaining.decrementAndGet() <= 0) {
            pc.finishJob.run();
            return;
        }
        schedulePostCapture(pc);
    }

    private RenderConfigDto buildRenderConfig(TriggerConfig t) {
        List<DestinationDto> dests = t.getDestinations().stream()
                .map(d -> new DestinationDto(d.getType(), d.getUrl()))
                .collect(Collectors.toList());
        return new RenderConfigDto(
                t.getFps(),
                t.getPixelsPerBlock(),
                t.getRadiusChunks(),
                "follow_player",
                true,
                dests);
    }

    private static final class PostCapture {
        final Player player;
        final TriggerConfig triggerConfig;
        final List<FrameSnapshot> preFrames;
        final List<FrameSnapshot> postFrames;
        final AtomicInteger postRemaining;
        final Runnable finishJob;
        final int radiusChunks;

        PostCapture(Player player, TriggerConfig triggerConfig, List<FrameSnapshot> preFrames,
                    List<FrameSnapshot> postFrames, AtomicInteger postRemaining, Runnable finishJob, int radiusChunks) {
            this.player = player;
            this.triggerConfig = triggerConfig;
            this.preFrames = preFrames;
            this.postFrames = postFrames;
            this.postRemaining = postRemaining;
            this.finishJob = finishJob;
            this.radiusChunks = radiusChunks;
        }
    }
}
