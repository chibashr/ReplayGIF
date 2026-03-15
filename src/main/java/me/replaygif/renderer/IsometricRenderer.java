package me.replaygif.renderer;

import me.replaygif.compat.AdventureTextUtil;
import me.replaygif.core.AttackRecord;
import me.replaygif.core.BlockRegistry;
import me.replaygif.core.EntitySnapshot;
import me.replaygif.core.ItemSerializer;
import me.replaygif.core.WorldSnapshot;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Shape;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Collectors;

import org.slf4j.Logger;

/**
 * Turns a list of WorldSnapshots into a list of BufferedImages (one per frame) using a fixed
 * isometric projection. Block pass uses painter's algorithm (sort by relX+relZ+relY) and
 * cut plane so only the "front" half of the volume is drawn; entities and overlays (trigger
 * border, death tint, gravestone) are drawn on top. Dimensions are computed from volume
 * and tile size so the pipeline does not depend on hardcoded resolution.
 */
public class IsometricRenderer {

    private static final short AIR_ORDINAL = 0;
    private static final Color TRIGGER_BORDER_COLOR = new Color(0xFFD700); // yellow
    private static final Color DEATH_OVERLAY_COLOR = new Color(180, 0, 0, 76);
    private static final Color MARKER_FALLBACK_COLOR = new Color(128, 128, 128);
    private static final Color CRITICAL_HIT_COLOR = new Color(0xFFAA00); // gold
    private static final Color SWEEP_ARC_COLOR = new Color(0xAAAAAA);

    /** Materials drawn with alpha 128 and SRC_OVER so blocks behind are visible. */
    private static final Set<String> TRANSPARENT_MATERIALS = Set.of(
            "GLASS", "WHITE_STAINED_GLASS", "ORANGE_STAINED_GLASS", "MAGENTA_STAINED_GLASS",
            "LIGHT_BLUE_STAINED_GLASS", "YELLOW_STAINED_GLASS", "LIME_STAINED_GLASS",
            "PINK_STAINED_GLASS", "GRAY_STAINED_GLASS", "LIGHT_GRAY_STAINED_GLASS",
            "CYAN_STAINED_GLASS", "PURPLE_STAINED_GLASS", "BLUE_STAINED_GLASS",
            "BROWN_STAINED_GLASS", "GREEN_STAINED_GLASS", "RED_STAINED_GLASS", "BLACK_STAINED_GLASS",
            "TINTED_GLASS", "BARRIER", "ICE", "PACKED_ICE", "BLUE_ICE",
            "WATER"
    );

    /** Materials that get per-frame hue shift (liquid shimmer). */
    private static final Set<String> LIQUID_MATERIALS = Set.of("WATER", "LAVA");

    private final int volumeSize;
    private final int tileWidth;
    private final int tileHeight;
    private final int cutOffset;
    /** Blocks at relY <= this are never culled; full ground layer. */
    private final int groundFullRelY;
    /** Vertical view offset in blocks; positive = show more ground below subject. */
    private final int viewCenterOffsetY;
    /** When false, draw all 3 faces per block; when true, only visible faces (computeFaceVisibility). */
    private final boolean blockFaceCulling;
    private final int imageWidth;
    private final int imageHeight;
    private final BlockColorMap blockColorMap;
    private final BlockRegistry blockRegistry;
    /** Pre-computed occlusion: true if block occludes adjacent faces (avoids per-frame getMaterial/isTransparent calls). */
    private final boolean[] occludesByOrdinal;
    private final BlockTextureRegistry blockTextureRegistry;
    private final EntitySpriteRegistry entitySpriteRegistry;
    private final SkinCache skinCache;
    private final HurtParticleSynthesizer hurtParticleSynthesizer;
    private final HudRenderer hudRenderer;
    /** Crack overlay images for block break progress (stage 0–9). Null = no overlay. */
    private final BufferedImage[] crackStages;
    /** Optional item textures for dropped item rendering; null = fallback color rectangle. */
    private final ItemTextureCache itemTextureCache;
    /** Cache of scaled textures (identity key) — textures scaled to tile dimensions are stable per renderer. */
    private final Map<BufferedImage, BufferedImage> scaledTextureCache = new IdentityHashMap<>();

    /** Block-only renderer when entity/skin registries are not available. */
    public IsometricRenderer(int volumeSize, int tileWidth, int tileHeight, int cutOffset,
                            BlockColorMap blockColorMap, BlockRegistry blockRegistry) {
        this(volumeSize, tileWidth, tileHeight, cutOffset, blockColorMap, blockRegistry, null, null, null, null, null, null, null, -1, 0, true);
    }

    /** Block-only renderer with explicit groundFullRelY (for tests). */
    public IsometricRenderer(int volumeSize, int tileWidth, int tileHeight, int cutOffset, int groundFullRelY,
                            BlockColorMap blockColorMap, BlockRegistry blockRegistry) {
        this(volumeSize, tileWidth, tileHeight, cutOffset, blockColorMap, blockRegistry, null, null, null, null, null, null, null, groundFullRelY, 0, true);
    }

    /**
     * Full renderer with optional entity and skin support. When entitySpriteRegistry and skinCache
     * are null, entity pass and gravestone are skipped; trigger border and death overlay still apply if context is set.
     * When blockTextureRegistry is non-null and has textures for a block, those are drawn instead of solid colors.
     */
    public IsometricRenderer(int volumeSize, int tileWidth, int tileHeight, int cutOffset,
                            BlockColorMap blockColorMap, BlockRegistry blockRegistry,
                            EntitySpriteRegistry entitySpriteRegistry, SkinCache skinCache) {
        this(volumeSize, tileWidth, tileHeight, cutOffset, blockColorMap, blockRegistry, null, entitySpriteRegistry, skinCache, null, null, null, null, 0, 0, true);
    }

    /**
     * Full renderer with optional block textures, entity and skin support.
     */
    public IsometricRenderer(int volumeSize, int tileWidth, int tileHeight, int cutOffset,
                            BlockColorMap blockColorMap, BlockRegistry blockRegistry,
                            BlockTextureRegistry blockTextureRegistry,
                            EntitySpriteRegistry entitySpriteRegistry, SkinCache skinCache) {
        this(volumeSize, tileWidth, tileHeight, cutOffset, blockColorMap, blockRegistry, blockTextureRegistry, entitySpriteRegistry, skinCache, null, null, null, null, 0, 0, true);
    }

    /**
     * Full renderer with optional hurt/death particle synthesizer (Stage 0.5 + 1g) and HUD (Stage 1l).
     */
    public IsometricRenderer(int volumeSize, int tileWidth, int tileHeight, int cutOffset,
                            BlockColorMap blockColorMap, BlockRegistry blockRegistry,
                            BlockTextureRegistry blockTextureRegistry,
                            EntitySpriteRegistry entitySpriteRegistry, SkinCache skinCache,
                            HurtParticleSynthesizer hurtParticleSynthesizer) {
        this(volumeSize, tileWidth, tileHeight, cutOffset, blockColorMap, blockRegistry, blockTextureRegistry, entitySpriteRegistry, skinCache, hurtParticleSynthesizer, null, null, null, 0, 0, true);
    }

    /**
     * Full renderer with optional crack overlay (block break progress). Use from plugin when crack_stage_0–9.png are bundled.
     */
    public IsometricRenderer(int volumeSize, int tileWidth, int tileHeight, int cutOffset,
                            BlockColorMap blockColorMap, BlockRegistry blockRegistry,
                            BlockTextureRegistry blockTextureRegistry,
                            EntitySpriteRegistry entitySpriteRegistry, SkinCache skinCache,
                            HurtParticleSynthesizer hurtParticleSynthesizer,
                            BufferedImage[] crackStages) {
        this(volumeSize, tileWidth, tileHeight, cutOffset, blockColorMap, blockRegistry, blockTextureRegistry, entitySpriteRegistry, skinCache, hurtParticleSynthesizer, null, crackStages, null, 0, 0, true);
    }

    /**
     * Full renderer with hurt/death synthesizer, no HUD/crack/item cache. Used by tests.
     */
    public IsometricRenderer(int volumeSize, int tileWidth, int tileHeight, int cutOffset,
                            BlockColorMap blockColorMap, BlockRegistry blockRegistry,
                            BlockTextureRegistry blockTextureRegistry,
                            EntitySpriteRegistry entitySpriteRegistry, SkinCache skinCache,
                            HurtParticleSynthesizer hurtParticleSynthesizer,
                            HudRenderer hudRenderer,
                            BufferedImage[] crackStages,
                            ItemTextureCache itemTextureCache) {
        this(volumeSize, tileWidth, tileHeight, cutOffset, blockColorMap, blockRegistry, blockTextureRegistry, entitySpriteRegistry, skinCache, hurtParticleSynthesizer, hudRenderer, crackStages, itemTextureCache, 0, 0, true);
    }

    /**
     * Full renderer with optional HUD and crack overlay. When hudRenderer is non-null, HUD is drawn after the death overlay.
     * When crackStages is non-null (length 10), block break overlay is drawn per snapshot.breakingStage.
     */
    public IsometricRenderer(int volumeSize, int tileWidth, int tileHeight, int cutOffset,
                            BlockColorMap blockColorMap, BlockRegistry blockRegistry,
                            BlockTextureRegistry blockTextureRegistry,
                            EntitySpriteRegistry entitySpriteRegistry, SkinCache skinCache,
                            HurtParticleSynthesizer hurtParticleSynthesizer,
                            HudRenderer hudRenderer,
                            BufferedImage[] crackStages,
                            ItemTextureCache itemTextureCache,
                            int groundFullRelY,
                            int viewCenterOffsetY) {
        this(volumeSize, tileWidth, tileHeight, cutOffset, blockColorMap, blockRegistry, blockTextureRegistry, entitySpriteRegistry, skinCache, hurtParticleSynthesizer, hudRenderer, crackStages, itemTextureCache, groundFullRelY, viewCenterOffsetY, true);
    }

    /** Full constructor including block face culling option. */
    public IsometricRenderer(int volumeSize, int tileWidth, int tileHeight, int cutOffset,
                            BlockColorMap blockColorMap, BlockRegistry blockRegistry,
                            BlockTextureRegistry blockTextureRegistry,
                            EntitySpriteRegistry entitySpriteRegistry, SkinCache skinCache,
                            HurtParticleSynthesizer hurtParticleSynthesizer,
                            HudRenderer hudRenderer,
                            BufferedImage[] crackStages,
                            ItemTextureCache itemTextureCache,
                            int groundFullRelY,
                            int viewCenterOffsetY,
                            boolean blockFaceCulling) {
        this.volumeSize = volumeSize;
        this.tileWidth = tileWidth;
        this.tileHeight = tileHeight;
        this.cutOffset = cutOffset;
        this.groundFullRelY = groundFullRelY;
        this.viewCenterOffsetY = viewCenterOffsetY;
        this.blockFaceCulling = blockFaceCulling;
        this.blockColorMap = blockColorMap;
        this.blockRegistry = blockRegistry;
        int ordCount = blockRegistry.getOrdinalCount();
        this.occludesByOrdinal = new boolean[ordCount];
        for (int i = 0; i < ordCount; i++) {
            if (i == AIR_ORDINAL) continue;
            Material m = blockRegistry.getMaterial((short) i);
            boolean transparent = m != null && TRANSPARENT_MATERIALS.contains(m.name());
            occludesByOrdinal[i] = !transparent;
        }
        this.blockTextureRegistry = blockTextureRegistry;
        this.entitySpriteRegistry = entitySpriteRegistry;
        this.skinCache = skinCache;
        this.hurtParticleSynthesizer = hurtParticleSynthesizer;
        this.hudRenderer = hudRenderer;
        this.crackStages = crackStages != null && crackStages.length >= 10 ? crackStages : null;
        this.itemTextureCache = itemTextureCache;
        int[] dims = computeImageDimensions();
        this.imageWidth = dims[0];
        this.imageHeight = dims[1];
    }

    /**
     * Stage 0.5: run hurt/death particle analysis on the frame list. Call once per render job before rendering frames.
     */
    public void analyzeHurtDeath(List<WorldSnapshot> frames) {
        if (hurtParticleSynthesizer != null) {
            hurtParticleSynthesizer.analyze(frames);
        }
    }

    /**
     * Prefetches assets (entity sprites, block textures, item textures) for all frames in parallel.
     * Call before the render loop to avoid sequential fetches during render.
     */
    public void prefetchAssetsForFrames(List<WorldSnapshot> frames, McAssetFetcher fetcher) {
        if (frames == null || frames.isEmpty() || fetcher == null) return;

        Set<EntityType> entityTypes = new HashSet<>();
        Set<String> blockTextureNames = new HashSet<>();
        Set<String> itemMaterialNames = new HashSet<>();

        for (WorldSnapshot snap : frames) {
            for (EntitySnapshot e : snap.entities) {
                if (e.type != EntityType.PLAYER) {
                    entityTypes.add(e.type);
                }
                if (e.droppedItemMaterial != null) {
                    String mat = ItemSerializer.getMaterialName(e.droppedItemMaterial);
                    if (mat != null && !mat.isEmpty()) itemMaterialNames.add(mat);
                }
            }
            if (blockTextureRegistry != null && snap.blocks != null) {
                blockTextureRegistry.collectTextureNamesForBlocks(snap.blocks, blockTextureNames);
            }
            addItemMaterial(itemMaterialNames, snap.mainHandItem);
            addItemMaterial(itemMaterialNames, snap.offHandItem);
            addItemMaterial(itemMaterialNames, snap.helmetItem);
            addItemMaterial(itemMaterialNames, snap.chestplateItem);
            addItemMaterial(itemMaterialNames, snap.leggingsItem);
            addItemMaterial(itemMaterialNames, snap.bootsItem);
            if (snap.hotbarItems != null) {
                for (String h : snap.hotbarItems) addItemMaterial(itemMaterialNames, h);
            }
        }

        fetcher.prefetchInParallel(entityTypes, blockTextureNames, itemMaterialNames);

        if (blockTextureRegistry != null) {
            blockTextureRegistry.prefetchFacesForFrames(frames);
        }
    }

    private static void addItemMaterial(Set<String> out, String compact) {
        if (compact == null || compact.isEmpty()) return;
        String mat = ItemSerializer.getMaterialName(compact);
        if (mat != null && !mat.isEmpty()) out.add(mat);
    }

    /** Carries trigger frame index and death/last-alive state so we draw border and overlay on the right frame. */
    public record RenderFrameContext(
            int triggerFrameIndex,
            String playerName,
            double lastAliveRelX, double lastAliveRelY, double lastAliveRelZ,
            boolean allFramesDead) {}

    /** Derived from volume and tile size so GIF dimensions are consistent and config-driven. Returns [width, height]. */
    public int[] computeImageDimensions() {
        int w = (volumeSize + volumeSize) * (tileWidth / 2) + tileWidth;
        int h = (volumeSize + volumeSize) * (tileHeight / 2) + (volumeSize * tileHeight) + tileHeight;
        return new int[] { w, h };
    }

    /** Width in pixels of each frame image. */
    public int getImageWidth() {
        return imageWidth;
    }

    /** Height in pixels of each frame image. */
    public int getImageHeight() {
        return imageHeight;
    }

    /**
     * Builds the block draw list by iterating all non-air blocks and applying the cut plane.
     * No occlusion culling — renders all blocks for a complete look (matches v0.1.0 behavior).
     * Sorted back-to-front for correct painter's order.
     */
    public List<BlockDrawEntry> buildBlockDrawList(WorldSnapshot snapshot) {
        short[] blocks = snapshot.blocks;
        int vol = snapshot.volumeSize;
        int half = vol / 2;
        List<BlockDrawEntry> list = new ArrayList<>();

        for (int x = 0; x < vol; x++) {
            for (int y = 0; y < vol; y++) {
                for (int z = 0; z < vol; z++) {
                    int idx = x * vol * vol + y * vol + z;
                    short ordinal = blocks[idx];
                    if (ordinal == AIR_ORDINAL) continue;
                    int relX = x - half;
                    int relY = y - half;
                    int relZ = z - half;
                    if (CutPlanePolicy.isCulled(relX, relY, relZ, cutOffset, groundFullRelY)) continue;
                    int sortKey = relX + relZ + relY;
                    list.add(new BlockDrawEntry(sortKey, relX, relY, relZ, ordinal));
                }
            }
        }

        list.sort((a, b) -> Integer.compare(b.sortKey, a.sortKey));
        return list;
    }

    /**
     * Logs diagnostic info about what will be rendered: block material counts, draw-list stats,
     * entity list (type, position, player/dead), and whether blocks use textures or solid colors.
     * Call before rendering to troubleshoot visibility/color issues (sand, water, trees, mobs).
     *
     * @param jobIdPrefix optional (e.g. UUID) to prefix log lines; null to omit
     */
    public void logRenderDiagnostics(WorldSnapshot snapshot, int frameIndex, String jobIdPrefix, Logger logger) {
        if (logger == null) return;
        String prefix = (jobIdPrefix != null ? "[" + jobIdPrefix + "] " : "");

        List<BlockDrawEntry> drawList = buildBlockDrawList(snapshot);
        int texturedCount = 0;
        if (blockTextureRegistry != null) {
            for (BlockDrawEntry e : drawList) {
                if (blockTextureRegistry.getFaces(e.materialOrdinal).isPresent()) {
                    texturedCount++;
                }
            }
        }
        String entitySummary = snapshot.entities.stream()
                .map(e -> e.type + (e.isPlayer ? "(player)" : ""))
                .collect(Collectors.joining(", "));
        logger.info("{}[frame {}] Rendering: {} blocks ({} textured, {} solid), {} entities [{}]",
                prefix, frameIndex, drawList.size(), texturedCount, drawList.size() - texturedCount,
                snapshot.entities.size(), entitySummary.isEmpty() ? "none" : entitySummary);

        if (!logger.isDebugEnabled()) return;

        int vol = snapshot.volumeSize;
        short[] blocks = snapshot.blocks;
        Map<String, Integer> materialCounts = new TreeMap<>();

        for (int i = 0; i < blocks.length; i++) {
            short ord = blocks[i];
            if (ord == AIR_ORDINAL) continue;
            Material m = blockRegistry.getMaterial(ord);
            String name = m != null ? m.name() : "ordinal_" + ord;
            materialCounts.merge(name, 1, Integer::sum);
        }

        List<Map.Entry<String, Integer>> topBlocks = materialCounts.entrySet().stream()
                .sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
                .limit(25)
                .collect(Collectors.toList());

        logger.debug("{}[frame {}] Block materials (top 25): {}", prefix, frameIndex,
                topBlocks.stream().map(e -> e.getKey() + "=" + e.getValue()).collect(Collectors.joining(", ")));

        logger.debug("{}[frame {}] Draw list: {} blocks visible (cut plane), {} textured, {} solid color",
                prefix, frameIndex, drawList.size(), texturedCount, drawList.size() - texturedCount);

        for (EntitySnapshot e : snapshot.entities) {
            String role = e.isPlayer ? "player" : (e.isDead ? "dead" : "mob");
            logger.debug("{}[frame {}] Entity {} at ({}, {}, {}) {} invisible={}",
                    prefix, frameIndex, e.type, e.relX, e.relY, e.relZ, role, e.invisible);
        }
        if (snapshot.entities.isEmpty()) {
            logger.debug("{}[frame {}] No entities in snapshot", prefix, frameIndex);
        }
    }

    /** Screen position of the top-center of a block at the given relative coords. */
    public Point project(int relX, int relY, int relZ) {
        return project((double) relX, (double) relY, (double) relZ);
    }

    /** Same as project(int) but for entity positions in double. */
    public Point project(double relX, double relY, double relZ) {
        int sx = imageWidth / 2 + (int) Math.round((relX - relZ) * (tileWidth / 2.0));
        int sy = imageHeight / 2 + (int) Math.round((relX + relZ) * (tileHeight / 2.0) - relY * tileHeight - viewCenterOffsetY * tileHeight);
        return new Point(sx, sy);
    }

    /**
     * Draws visible block faces. When BlockTextureRegistry has textures for this material,
     * draws them; otherwise uses BlockColorMap solid colors. Only faces not occluded by
     * adjacent blocks are drawn.
     */
    public void drawBlock(Graphics2D g, int screenX, int screenY, short materialOrdinal, int frameIndex,
                          FaceVisibility vis) {
        Optional<BlockFaceTextures> texOpt = blockTextureRegistry != null
                ? blockTextureRegistry.getFaces(materialOrdinal)
                : Optional.empty();

        if (texOpt.isPresent()) {
            drawBlockTextured(g, screenX, screenY, materialOrdinal, frameIndex, texOpt.get(), vis);
        } else {
            drawBlockColored(g, screenX, screenY, materialOrdinal, frameIndex, vis);
        }
    }

    private void drawBlockTextured(Graphics2D g, int screenX, int screenY, short materialOrdinal,
                                   int frameIndex, BlockFaceTextures tex, FaceVisibility vis) {
        boolean transparent = isTransparent(materialOrdinal);
        int halfW = tileWidth / 2;
        int halfH = tileHeight / 2;
        int th = tileHeight;

        BufferedImage topTex = scaledTexture(tex.top(), tileWidth, th);
        BufferedImage sideTex = scaledTexture(tex.side(), tileWidth, th);

        // Opaque blocks: full alpha on all faces. Shading (darker sides) is achieved by BlockColorMap
        // via RGB darkening for solid-color path; textures stay opaque. Using alpha for shading caused
        // semi-transparency and x-ray/ghosting of interior blocks.
        float alphaTop = transparent ? 0.5f : 1f;
        float alphaLeft = transparent ? 0.375f : 1f;
        float alphaRight = transparent ? 0.275f : 1f;

        if (vis.top()) {
            AffineTransform at = new AffineTransform(
                    (double) halfW / tileWidth, (double) halfH / tileWidth,
                    (double) -halfW / th, (double) halfH / th,
                    screenX, screenY);
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alphaTop));
            g.drawImage(topTex, at, null);
        }
        if (vis.left()) {
            AffineTransform at = new AffineTransform(
                    (double) halfW / tileWidth, (double) halfH / tileWidth,
                    0, 1,
                    screenX - halfW, screenY + halfH);
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alphaLeft));
            g.drawImage(sideTex, at, null);
        }
        if (vis.right()) {
            AffineTransform at = new AffineTransform(
                    (double) halfW / tileWidth, (double) -halfH / tileWidth,
                    0, 1,
                    screenX, screenY + th);
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alphaRight));
            g.drawImage(sideTex, at, null);
        }
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1f));
    }

    private BufferedImage scaledTexture(BufferedImage src, int w, int h) {
        BufferedImage cached = scaledTextureCache.get(src);
        if (cached != null && cached.getWidth() == w && cached.getHeight() == h) {
            return cached;
        }
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D sg = out.createGraphics();
        sg.setComposite(AlphaComposite.SrcOver);
        sg.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        sg.setRenderingHint(RenderingHints.KEY_ALPHA_INTERPOLATION, RenderingHints.VALUE_ALPHA_INTERPOLATION_QUALITY);
        sg.setRenderingHint(RenderingHints.KEY_COLOR_RENDERING, RenderingHints.VALUE_COLOR_RENDER_QUALITY);
        sg.drawImage(src, 0, 0, w, h, null);
        sg.dispose();
        scaledTextureCache.put(src, out);
        return out;
    }

    /** Draws crack overlay on all three block faces. Opacity 30% + (stage/9)*40% (30–70%). */
    private void drawCrackOverlay(Graphics2D g, int screenX, int screenY, int stage) {
        if (stage < 0 || stage > 9 || crackStages == null || crackStages[stage] == null) {
            return;
        }
        float alpha = 0.3f + (stage / 9.0f) * 0.4f;
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
        int halfW = tileWidth / 2;
        int halfH = tileHeight / 2;
        int th = tileHeight;
        BufferedImage crack = scaledTexture(crackStages[stage], tileWidth, th);
        AffineTransform topAt = new AffineTransform(
                (double) halfW / tileWidth, (double) halfH / tileWidth,
                (double) -halfW / th, (double) halfH / th,
                screenX, screenY);
        g.drawImage(crack, topAt, null);
        AffineTransform leftAt = new AffineTransform(
                (double) halfW / tileWidth, (double) halfH / tileWidth,
                0, 1,
                screenX - halfW, screenY + halfH);
        g.drawImage(crack, leftAt, null);
        AffineTransform rightAt = new AffineTransform(
                (double) halfW / tileWidth, (double) -halfH / tileWidth,
                0, 1,
                screenX, screenY + th);
        g.drawImage(crack, rightAt, null);
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1f));
    }

    private static final Color WATER_BLUE = new Color(0x3F76E4);

    private void drawBlockColored(Graphics2D g, int screenX, int screenY, short materialOrdinal, int frameIndex,
                                  FaceVisibility vis) {
        BlockFaceColors faces = blockColorMap.getFaces(materialOrdinal);
        boolean transparent = isTransparent(materialOrdinal);
        boolean liquid = isLiquid(materialOrdinal);
        Material mat = blockRegistry.getMaterial(materialOrdinal);

        Color top = faces.top();
        Color left = faces.left();
        Color right = faces.right();
        if (mat != null && mat == Material.WATER) {
            top = WATER_BLUE;
            left = new Color((int) (WATER_BLUE.getRed() * 0.75), (int) (WATER_BLUE.getGreen() * 0.75), (int) (WATER_BLUE.getBlue() * 0.75));
            right = new Color((int) (WATER_BLUE.getRed() * 0.55), (int) (WATER_BLUE.getGreen() * 0.55), (int) (WATER_BLUE.getBlue() * 0.55));
        } else if (liquid) {
            int shiftDegrees = (frameIndex % 10) * 2;
            top = shiftHue(top, shiftDegrees);
            left = shiftHue(left, shiftDegrees);
            right = shiftHue(right, shiftDegrees);
        }
        if (transparent) {
            top = withAlpha(top, 128);
            left = withAlpha(left, 128);
            right = withAlpha(right, 128);
        }

        int halfW = tileWidth / 2;
        int halfH = tileHeight / 2;
        int th = tileHeight;

        if (vis.top()) {
            int[] topX = { screenX, screenX + halfW, screenX, screenX - halfW };
            int[] topY = { screenY, screenY + halfH, screenY + th, screenY + halfH };
            g.setColor(top);
            g.fillPolygon(topX, topY, 4);
        }
        if (vis.left()) {
            int[] leftX = { screenX - halfW, screenX, screenX, screenX - halfW };
            int[] leftY = { screenY + halfH, screenY + th, screenY + th + th, screenY + halfH + th };
            g.setColor(left);
            g.fillPolygon(leftX, leftY, 4);
        }
        if (vis.right()) {
            int[] rightX = { screenX, screenX + halfW, screenX + halfW, screenX };
            int[] rightY = { screenY + th, screenY + halfH, screenY + halfH + th, screenY + th + th };
            g.setColor(right);
            g.fillPolygon(rightX, rightY, 4);
        }
    }

    /** Renders one frame without entities or overlays; context null means block (+ emissive) only. */
    public BufferedImage renderFrame(WorldSnapshot snapshot, int frameIndex) {
        return renderFrame(snapshot, frameIndex, null);
    }

    /**
     * Full frame: blocks (with emissive glow), then entities (if context and registries set),
     * then trigger border on the trigger frame index, then death overlay/gravestone when applicable.
     */
    public BufferedImage renderFrame(WorldSnapshot snapshot, int frameIndex, RenderFrameContext context) {
        BufferedImage image = new BufferedImage(imageWidth, imageHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        try {
            // Ensure full opacity for block/entity drawing; avoid residual composite from prior state
            g.setComposite(AlphaComposite.SrcOver);
            g.setRenderingHint(RenderingHints.KEY_ALPHA_INTERPOLATION, RenderingHints.VALUE_ALPHA_INTERPOLATION_QUALITY);
            g.setRenderingHint(RenderingHints.KEY_COLOR_RENDERING, RenderingHints.VALUE_COLOR_RENDER_QUALITY);
            // Block pass
            List<BlockDrawEntry> drawList = buildBlockDrawList(snapshot);
            List<EmissiveGlow> glowPositions = new ArrayList<>();
            int vol = snapshot.volumeSize;
            short[] blocks = snapshot.blocks;
            int half = vol / 2;

            for (BlockDrawEntry e : drawList) {
                Point p = project(e.relX, e.relY, e.relZ);
                // Always draw top and side faces so the GIF looks complete.
                FaceVisibility vis = new FaceVisibility(true, true, true);
                drawBlock(g, p.x, p.y, e.materialOrdinal, frameIndex, vis);
                if (blockColorMap.isEmissive(e.materialOrdinal)) {
                    BlockFaceColors faces = blockColorMap.getFaces(e.materialOrdinal);
                    Color glowColor = faces.top();
                    for (int dx = -1; dx <= 1; dx++) {
                        for (int dy = -1; dy <= 1; dy++) {
                            for (int dz = -1; dz <= 1; dz++) {
                                if (dx == 0 && dy == 0 && dz == 0) continue;
                                int nx = e.relX + dx + half;
                                int ny = e.relY + dy + half;
                                int nz = e.relZ + dz + half;
                                if (nx < 0 || nx >= vol || ny < 0 || ny >= vol || nz < 0 || nz >= vol) continue;
                                int idx = nx * vol * vol + ny * vol + nz;
                                if (blocks[idx] == AIR_ORDINAL) {
                                    glowPositions.add(new EmissiveGlow(e.relX + dx, e.relY + dy, e.relZ + dz, glowColor));
                                }
                            }
                        }
                    }
                }
            }

            // Emissive glow pass
            Set<String> drawn = new HashSet<>();
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.2f));
            for (EmissiveGlow glow : glowPositions) {
                String key = glow.relX + "," + glow.relY + "," + glow.relZ;
                if (drawn.contains(key)) continue;
                drawn.add(key);
                Point p = project(glow.relX, glow.relY, glow.relZ);
                g.setColor(glow.color);
                int halfW = tileWidth / 2;
                g.fillRect(p.x - halfW, p.y, tileWidth, tileHeight);
            }
            g.setComposite(AlphaComposite.SrcOver);

            // Entity pass (1f)
            if (context != null && entitySpriteRegistry != null && skinCache != null) {
                drawEntities(g, snapshot, context.playerName(), frameIndex);
            }

            // Hurt and death particles (1g)
            if (hurtParticleSynthesizer != null) {
                hurtParticleSynthesizer.draw(g, frameIndex, this::project);
            }

            // Combat particle pass (1g.5) — critical hit stars and sweep arc
            drawCombatParticles(g, snapshot, frameIndex);

            // Trigger frame marker (1h)
            if (context != null && frameIndex == context.triggerFrameIndex()) {
                drawTriggerBorder(g);
            }

            // Post-death overlay (1i)
            if (context != null) {
                boolean showDeath = context.allFramesDead()
                        || (snapshot.playerHealth == 0.0f && frameIndex > context.triggerFrameIndex());
                if (showDeath) {
                    drawDeathOverlay(g, context);
                }
            }

            if (hudRenderer != null) {
                hudRenderer.drawHud(g, snapshot, imageWidth, imageHeight, frameIndex);
            } else {
                HudRenderer.draw(g, snapshot, imageWidth, imageHeight, tileHeight);
            }
        } finally {
            g.dispose();
        }
        return image;
    }

    /** Vanilla ambient particle colors per effect (decisions.md). Default AEC #A000FF. Dragon breath #7B00C8. */
    private static Color aecEffectColor(String aecEffectName) {
        if (aecEffectName == null) return new Color(0xA000FF);
        return switch (aecEffectName.toLowerCase()) {
            case "strength" -> new Color(0x932423);
            case "speed" -> new Color(0x7CAFC4);
            case "slowness" -> new Color(0x5A6C81);
            case "poison" -> new Color(0x4E9331);
            case "regeneration" -> new Color(0xCD5CAB);
            case "resistance" -> new Color(0x99453A);
            case "fire_resistance" -> new Color(0xE49A3A);
            case "water_breathing" -> new Color(0x2E5299);
            case "night_vision" -> new Color(0x1F1FA1);
            case "wither" -> new Color(0x352A27);
            case "absorption" -> new Color(0x2552A5);
            case "instant_damage" -> new Color(0x7B00C8);  // dragon breath
            default -> new Color(0xA000FF);
        };
    }

    private void drawEntities(Graphics2D g, WorldSnapshot snapshot, String playerName, int frameIndex) {
        List<EntitySnapshot> entities = new ArrayList<>(snapshot.entities);
        entities.sort(Comparator.comparingDouble(e -> e.relX + e.relZ + e.relY));

        for (EntitySnapshot e : entities) {
            // Ground-standing entities (player, mobs): project feet onto the top of the block below.
            // Floating entities (fishing hook, dropped item, XP orb): use actual position.
            boolean groundStanding = e.isPlayer || e.type == EntityType.PLAYER
                    || (e.type != EntityType.FISHING_HOOK && e.type != EntityType.DROPPED_ITEM && e.type != EntityType.EXPERIENCE_ORB);
            double anchorRelY = groundStanding ? e.relY - 1 : e.relY;
            Point p = project(e.relX, anchorRelY, e.relZ);
            int screenX = p.x;
            int screenY = p.y;

            if (e.type == EntityType.AREA_EFFECT_CLOUD && e.aecRadius >= 0) {
                drawAreaEffectCloud(g, e, screenX, screenY, frameIndex);
                continue;
            }
            if (e.type == EntityType.DROPPED_ITEM && e.droppedItemMaterial != null) {
                drawDroppedItem(g, e, screenX, screenY, frameIndex);
                continue;
            }
            if (e.type == EntityType.EXPERIENCE_ORB) {
                drawExperienceOrb(g, screenX, screenY, frameIndex);
                continue;
            }

            double w = e.boundingWidth;
            double h = e.boundingHeight;
            int spriteW = (int) Math.round(w * tileWidth);
            int spriteH = (int) Math.round(h * tileHeight * 2);
            if (spriteW < 1) spriteW = 1;  // SnapshotScheduler clamps to 1e-6; ensure pixel dimensions are at least 1 to avoid invalid draw
            if (spriteH < 1) spriteH = 1;

            BufferedImage sprite = null;
            if (e.isPlayer || e.type == EntityType.PLAYER) {
                Optional<BufferedImage> body = skinCache.getBody(e.uuid);
                sprite = body.orElseGet(skinCache::getPlaceholderBody);
                if (sprite == null) {
                    sprite = skinCache.getPlaceholderBody();
                }
            } else {
                Optional<BufferedImage> reg = entitySpriteRegistry.getSprite(e.type);
                sprite = reg.orElse(null);
            }
            if (sprite == null) {
                Color markerColor = entitySpriteRegistry != null
                        ? entitySpriteRegistry.getMarkerColorOrDerived(e.type)
                        : MARKER_FALLBACK_COLOR;
                sprite = createColoredMarker(spriteW, spriteH, markerColor);
            }

            double poseScaleH = e.isDead ? 1.0 : poseHeightScale(e.pose);
            int poseSpriteH = (int) Math.round(spriteH * poseScaleH);
            if (poseSpriteH < 1) poseSpriteH = 1;
            BufferedImage scaled = scaleSprite(sprite, spriteW, poseSpriteH);
            int bobOffsetY = (int) (Math.sin(frameIndex * 0.25) * 2);
            int left = screenX - spriteW / 2;
            int top = (int) (screenY + tileHeight - poseSpriteH + bobOffsetY);
            int drawLeft = left;
            int drawTop = top;
            int drawW = spriteW;
            int drawH = poseSpriteH;

            if (e.isDead) {
                // Death tilt: rotate sprite 90° clockwise; draw with bottom-left at (screenX - spriteW/2, screenY)
                AffineTransform prev = g.getTransform();
                g.translate(screenX - spriteW / 2 - poseSpriteH, screenY);
                g.rotate(-Math.PI / 2);
                drawLeft = 0;
                drawTop = 0;
                drawW = poseSpriteH;
                drawH = spriteW;
                if (e.invisible) {
                    g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.4f));
                }
                g.drawImage(scaled, 0, 0, null);
                g.setTransform(prev);
                g.setComposite(AlphaComposite.SrcOver);
                drawLeft = screenX - spriteW / 2 - poseSpriteH;
                drawTop = screenY;
            } else {
                if (e.invisible) {
                    g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.4f));
                }
                double rotRad = Math.toRadians(-e.yaw);
                if (Math.abs(rotRad) > 0.001) {
                    AffineTransform prev = g.getTransform();
                    g.translate(screenX, (int) (screenY + tileHeight + bobOffsetY));
                    g.rotate(rotRad);
                    g.translate(-spriteW / 2.0, -poseSpriteH);
                    g.drawImage(scaled, 0, 0, null);
                    g.setTransform(prev);
                } else {
                    g.drawImage(scaled, left, top, null);
                }
                g.setComposite(AlphaComposite.SrcOver);
            }

            if (e.hurtProgress > 0.0f) {
                int flashOpacity = Math.min(200, (int) (e.hurtProgress * 200));
                g.setColor(new Color(255, 0, 0, flashOpacity));
                g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, flashOpacity / 255f));
                g.fillRect(drawLeft, drawTop, drawW, drawH);
                g.setComposite(AlphaComposite.SrcOver);
            }

            if (e.onFire && entitySpriteRegistry.getFireOverlay() != null) {
                BufferedImage fire = scaleSprite(entitySpriteRegistry.getFireOverlay(), spriteW, poseSpriteH);
                g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.7f));
                if (e.isDead) {
                    AffineTransform prev = g.getTransform();
                    g.translate(screenX - spriteW / 2 - poseSpriteH, screenY);
                    g.rotate(-Math.PI / 2);
                    g.drawImage(fire, 0, 0, null);
                    g.setTransform(prev);
                } else {
                    double rotRad = Math.toRadians(-e.yaw);
                    if (Math.abs(rotRad) > 0.001) {
                        AffineTransform prev = g.getTransform();
                        g.translate(screenX, (int) (screenY + tileHeight + bobOffsetY));
                        g.rotate(rotRad);
                        g.translate(-spriteW / 2.0, -poseSpriteH);
                        g.drawImage(fire, 0, 0, null);
                        g.setTransform(prev);
                    } else {
                        g.drawImage(fire, left, top, null);
                    }
                }
                g.setComposite(AlphaComposite.SrcOver);
            }

            String rawTag = e.isPlayer ? (playerName != null ? playerName : "") : (e.customName != null ? e.customName : null);
            String nameTag = rawTag != null ? AdventureTextUtil.stripLegacyFormatting(rawTag) : "";
            if (!nameTag.isEmpty()) {
                drawNameTag(g, drawLeft, drawTop, drawW, drawH, nameTag);
            }

            if (e.type == EntityType.FISHING_HOOK && e.shooterUUID != null) {
                EntitySnapshot shooter = null;
                for (EntitySnapshot s : snapshot.entities) {
                    if (e.shooterUUID.equals(s.uuid)) {
                        shooter = s;
                        break;
                    }
                }
                if (shooter != null) {
                    Point sp = project(shooter.relX, shooter.relY, shooter.relZ);
                    int shSpriteH = (int) Math.round(shooter.boundingHeight * tileHeight * 2);
                    if (shSpriteH < 1) shSpriteH = 1;
                    int handX = sp.x + tileWidth / 4;
                    int handY = (int) (sp.y + shSpriteH * 0.5);
                    int bobberX = screenX;
                    int bobberY = screenY;
                    double dx = bobberX - handX;
                    double dy = bobberY - handY;
                    double dist = Math.sqrt(dx * dx + dy * dy);
                    double offset = dist * 0.1;
                    int midX = (handX + bobberX) / 2;
                    int midY = (int) ((handY + bobberY) / 2.0 + offset);
                    g.setColor(new Color(0x555555));
                    g.setStroke(new BasicStroke(1f));
                    g.drawLine(handX, handY, midX, midY);
                    g.drawLine(midX, midY, bobberX, bobberY);
                }
            }
        }
    }

    private void drawAreaEffectCloud(Graphics2D g, EntitySnapshot e, int screenX, int screenY, int frameIndex) {
        double screenRadius = e.aecRadius * tileWidth;
        int ellipseWidth = (int) (screenRadius * 2);
        int ellipseHeight = (int) screenRadius;
        if (ellipseWidth < 2) ellipseWidth = 2;
        if (ellipseHeight < 1) ellipseHeight = 1;

        double angleRad = Math.toRadians(frameIndex * 5);
        double offsetX = screenRadius * 0.15 * Math.cos(angleRad);
        double offsetY = screenRadius * 0.15 * Math.sin(angleRad);
        int cx = screenX + (int) offsetX;
        int cy = screenY + (int) offsetY;

        Color baseColor = aecEffectColor(e.aecEffectName);

        g.rotate(angleRad, cx, cy);
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.35f));
        g.setColor(baseColor);
        g.fillOval(cx - ellipseWidth / 2, cy - ellipseHeight / 2, ellipseWidth, ellipseHeight);

        int innerW = (int) (ellipseWidth * 0.6);
        int innerH = (int) (ellipseHeight * 0.6);
        if (innerW >= 1 && innerH >= 1) {
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.2f));
            g.fillOval(cx - innerW / 2, cy - innerH / 2, innerW, innerH);
        }
        g.rotate(-angleRad, cx, cy);
        g.setComposite(AlphaComposite.SrcOver);
    }

    private static final Color DROPPED_ITEM_FALLBACK_COLOR = new Color(0x888888);
    private static final Color XP_ORB_COLOR = new Color(0x7CFC00);
    private static final Color XP_ORB_HIGHLIGHT = new Color(0xFFFFFF);
    private static final Color ENCHANTMENT_GLINT = new Color(0x80, 0x00, 0xFF, 80);

    private void drawDroppedItem(Graphics2D g, EntitySnapshot e, int screenX, int screenY, int frameIndex) {
        int bobOffsetY = (int) (Math.sin(frameIndex * 0.4) * 2);
        int size = Math.max(1, (int) (tileWidth * 0.6));
        int itemCenterY = screenY + bobOffsetY;
        int left = screenX - size / 2;
        int top = itemCenterY - size / 2;

        drawFloatingShadow(g, screenX, screenY, frameIndex);

        BufferedImage itemImg = null;
        if (itemTextureCache != null) {
            String material = ItemSerializer.getMaterialName(e.droppedItemMaterial);
            itemImg = material != null ? itemTextureCache.getTexture(material).orElse(null) : null;
        }
        if (itemImg == null) {
            itemImg = createColoredMarker(size, size, DROPPED_ITEM_FALLBACK_COLOR);
        }
        BufferedImage scaled = scaleSprite(itemImg, size, size);
        g.drawImage(scaled, left, top, null);
        if (ItemSerializer.isEnchanted(e.droppedItemMaterial)) {
            drawEnchantmentGlint(g, left, top, size, size, frameIndex);
        }
    }

    private void drawExperienceOrb(Graphics2D g, int screenX, int screenY, int frameIndex) {
        int bobOffsetY = (int) (Math.sin(frameIndex * 0.4) * 2);
        int diameter = Math.max(4, tileWidth / 4);
        int r = diameter / 2;
        int cy = screenY + bobOffsetY;

        drawFloatingShadow(g, screenX, screenY, frameIndex);

        g.setColor(XP_ORB_COLOR);
        g.fillOval(screenX - r, cy - r, diameter, diameter);
        g.setColor(XP_ORB_HIGHLIGHT);
        g.fillOval(screenX - r + 1, cy - r + 1, 2, 2);
    }

    private void drawEnchantmentGlint(Graphics2D g, int left, int top, int width, int height, int frameIndex) {
        if (width < 1 || height < 1) return;
        int bandWidth = Math.max(1, width / 3);
        int period = width + height;
        int glintOffset = (frameIndex * 2) % (period > 0 ? period : 1);
        double t0 = (double) glintOffset / (width + height);
        double t1 = (double) (glintOffset + bandWidth) / (width + height);
        double x0 = t0 * width;
        double y0 = t0 * height;
        double x1 = t1 * width;
        double y1 = t1 * height;
        double len = Math.hypot(width, height);
        if (len < 1e-6) return;
        double perpX = -height / len;
        double perpY = width / len;
        int ext = (int) (2 * Math.max(width, height));
        int[] px = { left + (int) (x0 - ext * perpX), left + (int) (x0 + ext * perpX),
                left + (int) (x1 + ext * perpX), left + (int) (x1 - ext * perpX) };
        int[] py = { top + (int) (y0 - ext * perpY), top + (int) (y0 + ext * perpY),
                top + (int) (y1 + ext * perpY), top + (int) (y1 - ext * perpY) };
        Shape prevClip = g.getClip();
        g.setClip(left, top, width, height);
        g.setColor(ENCHANTMENT_GLINT);
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, ENCHANTMENT_GLINT.getAlpha() / 255f));
        g.fillPolygon(px, py, 4);
        g.setComposite(AlphaComposite.SrcOver);
        g.setClip(prevClip);
    }

    private void drawFloatingShadow(Graphics2D g, int screenX, int screenY, int frameIndex) {
        int shadowW = (int) (tileWidth * 0.5);
        int shadowH = (int) (tileWidth * 0.2);
        if (shadowW < 1) shadowW = 1;
        if (shadowH < 1) shadowH = 1;
        int shadowY = screenY + (int) (tileHeight * 0.5);
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.3f));
        g.setColor(Color.BLACK);
        g.fillOval(screenX - shadowW / 2, shadowY - shadowH / 2, shadowW, shadowH);
        g.setComposite(AlphaComposite.SrcOver);
    }

    private BufferedImage createColoredMarker(int w, int h, Color color) {
        Color base = color != null ? color : MARKER_FALLBACK_COLOR;
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        try {
            g.setColor(base);
            g.fillRect(0, 0, w, h);
            int edge = Math.max(1, Math.min(w, h) / 8);
            g.setColor(darker(base, 0.6f));
            g.fillRect(0, 0, w, edge);
            g.fillRect(0, 0, edge, h);
            g.setColor(brighter(base, 1.15f));
            g.fillRect(0, h - edge, w, edge);
            g.fillRect(w - edge, 0, edge, h);
        } finally {
            g.dispose();
        }
        return img;
    }

    private static Color darker(Color c, float factor) {
        return new Color(
                Math.max(0, (int) (c.getRed() * factor)),
                Math.max(0, (int) (c.getGreen() * factor)),
                Math.max(0, (int) (c.getBlue() * factor)));
    }

    private static Color brighter(Color c, float factor) {
        return new Color(
                Math.min(255, (int) (c.getRed() * factor)),
                Math.min(255, (int) (c.getGreen() * factor)),
                Math.min(255, (int) (c.getBlue() * factor)));
    }

    private static BufferedImage scaleSprite(BufferedImage src, int w, int h) {
        if (src.getWidth() == w && src.getHeight() == h) return src;
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(src, 0, 0, w, h, null);
        g.dispose();
        return out;
    }

    private void drawNameTag(Graphics2D g, int spriteLeft, int spriteTop, int spriteW, int spriteH, String text) {
        g.setFont(new Font(Font.MONOSPACED, Font.PLAIN, tileHeight));
        int textY = spriteTop - 2;
        int textX = spriteLeft + spriteW / 2;
        g.setColor(Color.BLACK);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                if (dx != 0 || dy != 0) {
                    g.drawString(text, textX + dx - g.getFontMetrics().stringWidth(text) / 2, textY + dy);
                }
            }
        }
        g.setColor(Color.WHITE);
        g.drawString(text, textX - g.getFontMetrics().stringWidth(text) / 2, textY);
    }

    /** Stage 1g.5: critical hit stars (6 gold crosses) and sweep arc for attacks this frame. */
    private void drawCombatParticles(Graphics2D g, WorldSnapshot snapshot, int frameIndex) {
        List<AttackRecord> attacks = snapshot.attacksThisFrame;
        if (attacks == null || attacks.isEmpty()) {
            return;
        }
        double radiusCrit = tileWidth * 0.8;
        int crossHalf = 2; // 4px cross
        double radiusSweep = tileWidth * 1.5;

        for (AttackRecord atk : attacks) {
            Point targetScreen = targetScreenPosition(snapshot, atk);
            int tx = targetScreen.x;
            int ty = targetScreen.y;

            if (atk.isCritical) {
                g.setColor(CRITICAL_HIT_COLOR);
                for (int i = 0; i < 6; i++) {
                    double angleDeg = i * 60 + frameIndex * 15;
                    double angleRad = Math.toRadians(angleDeg);
                    int px = tx + (int) Math.round(Math.cos(angleRad) * radiusCrit);
                    int py = ty + (int) Math.round(Math.sin(angleRad) * radiusCrit);
                    g.fillRect(px - crossHalf, py - 1, crossHalf * 2, 2);
                    g.fillRect(px - 1, py - crossHalf, 2, crossHalf * 2);
                }
            }
            if (atk.isSweep) {
                g.setColor(SWEEP_ARC_COLOR);
                g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.8f));
                g.setStroke(new BasicStroke(3f));
                int d = (int) (radiusSweep * 2);
                g.drawArc(tx - d / 2, ty - d / 2, d, d, 20, 140);
                g.setStroke(new BasicStroke(1f));
                g.setComposite(AlphaComposite.SrcOver);
            }
        }
    }

    private Point targetScreenPosition(WorldSnapshot snapshot, AttackRecord atk) {
        EntitySnapshot target = findEntityByUuid(snapshot, atk.targetUUID);
        double relX = target != null ? target.relX : atk.targetRelX;
        double relY = target != null ? target.relY : atk.targetRelY;
        double relZ = target != null ? target.relZ : atk.targetRelZ;
        return project(relX, relY, relZ);
    }

    private static EntitySnapshot findEntityByUuid(WorldSnapshot snapshot, UUID uuid) {
        for (EntitySnapshot e : snapshot.entities) {
            if (e.uuid.equals(uuid)) return e;
        }
        return null;
    }

    private void drawTriggerBorder(Graphics2D g) {
        g.setColor(TRIGGER_BORDER_COLOR);
        g.setStroke(new BasicStroke(2f));
        g.drawRect(1, 1, imageWidth - 2, imageHeight - 2);
    }

    private void drawDeathOverlay(Graphics2D g, RenderFrameContext context) {
        g.setColor(DEATH_OVERLAY_COLOR);
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, DEATH_OVERLAY_COLOR.getAlpha() / 255f));
        g.fillRect(0, 0, imageWidth, imageHeight);
        g.setComposite(AlphaComposite.SrcOver);

        double gx = context.lastAliveRelX();
        double gy = context.lastAliveRelY();
        double gz = context.lastAliveRelZ();
        if (context.allFramesDead()) {
            gx = gy = gz = 0;
        }
        BufferedImage stone = entitySpriteRegistry != null ? entitySpriteRegistry.getGravestone() : null;
        if (stone != null) {
            int gw = tileWidth;
            int gh = tileHeight * 2;
            BufferedImage scaled = scaleSprite(stone, gw, gh);
            Point p = project(gx, gy, gz);
            int left = p.x - gw / 2;
            int top = (int) (p.y + tileHeight - gh);
            g.drawImage(scaled, left, top, null);
        }
    }

    private boolean isTransparent(short ordinal) {
        Material m = blockRegistry.getMaterial(ordinal);
        return m != null && TRANSPARENT_MATERIALS.contains(m.name());
    }

    private boolean isLiquid(short ordinal) {
        Material m = blockRegistry.getMaterial(ordinal);
        return m != null && LIQUID_MATERIALS.contains(m.name());
    }

    /** Height scale for pose (e.g. SNEAKING = shorter). Returns 1.0 for standing. */
    private static double poseHeightScale(String pose) {
        if (pose == null) return 1.0;
        return switch (pose) {
            case "SNEAKING", "CROUCHING" -> 0.833;  // 1.5/1.8 blocks
            case "SWIMMING", "FALL_FLYING" -> 0.6;  // flatter
            case "SLEEPING", "DYING" -> 0.3;
            default -> 1.0;
        };
    }

    private static Color withAlpha(Color c, int alpha) {
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), Math.min(255, Math.max(0, alpha)));
    }

    private static Color shiftHue(Color c, int degrees) {
        float[] hsb = Color.RGBtoHSB(c.getRed(), c.getGreen(), c.getBlue(), null);
        float h = hsb[0] * 360f;
        h = (h + degrees) % 360f;
        if (h < 0) h += 360f;
        int rgb = Color.HSBtoRGB(h / 360f, hsb[1], hsb[2]);
        return new Color((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF);
    }

    /** Which faces of a block are visible (not occluded by adjacent blocks). */
    public record FaceVisibility(boolean top, boolean left, boolean right) {}

    /**
     * Determines which block faces are visible: top (+Y), left (-X), right (-Z).
     * A face is visible when the neighbor in that direction is air, transparent, or out of volume.
     * Transparent blocks (glass, water, ice, etc.) do not occlude — faces bordering them are drawn.
     */
    private FaceVisibility computeFaceVisibility(int relX, int relY, int relZ,
                                                 short[] blocks, int vol, int half) {
        int x = relX + half;
        int y = relY + half;
        int z = relZ + half;
        boolean top = y + 1 >= vol || !occludes(blocks[x * vol * vol + (y + 1) * vol + z]);
        boolean left = x - 1 < 0 || !occludes(blocks[(x - 1) * vol * vol + y * vol + z]);
        boolean right = z - 1 < 0 || !occludes(blocks[x * vol * vol + y * vol + (z - 1)]);
        return new FaceVisibility(top, left, right);
    }

    /** True if this block occludes adjacent faces (solid, not air or transparent). */
    private boolean occludes(short ordinal) {
        return ordinal > 0 && ordinal < occludesByOrdinal.length && occludesByOrdinal[ordinal];
    }

    /** Single block entry for the sorted draw list; sortKey = relX+relZ+relY for painter's order. */
    public record BlockDrawEntry(int sortKey, int relX, int relY, int relZ, short materialOrdinal) {}

    private record EmissiveGlow(int relX, int relY, int relZ, Color color) {}
}
