package me.replaygif.renderer;

import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.profile.PlayerTextures;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * UUID → 8×8 face BufferedImage with TTL. On player join (if skin rendering
 * enabled): async HTTP GET to skin URL, parse 64×64 PNG, extract face region
 * (pixels 8–15 on both axes). getFace(UUID) returns Optional; fallback is
 * player_placeholder.png from resources.
 */
public class SkinCache {

    private static final String PLACEHOLDER_RESOURCE = "entity_sprites_default/player_placeholder.png";
    private static final int FACE_X = 8;
    private static final int FACE_Y = 8;
    private static final int FACE_SIZE = 8;

    private final JavaPlugin plugin;
    private final boolean enabled;
    private final int ttlSeconds;
    private final ConcurrentHashMap<UUID, CachedFace> cache = new ConcurrentHashMap<>();
    private volatile BufferedImage placeholder;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    private final ExecutorService executor = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "ReplayGif-SkinCache");
        t.setDaemon(true);
        return t;
    });

    public SkinCache(JavaPlugin plugin, boolean enabled, int ttlSeconds) {
        this.plugin = plugin;
        this.enabled = enabled;
        this.ttlSeconds = ttlSeconds;
    }

    /**
     * Call when a player joins; if skin rendering is enabled, fetches skin URL
     * and caches the face asynchronously.
     */
    public void onPlayerJoin(Player player) {
        if (!enabled) {
            return;
        }
        PlayerTextures textures = player.getPlayerProfile().getTextures();
        if (textures == null || textures.isEmpty()) {
            return;
        }
        try {
            java.net.URL skinUrl = textures.getSkin();
            if (skinUrl == null) {
                return;
            }
            URI uri = skinUrl.toURI();
            UUID uuid = player.getUniqueId();
            executor.submit(() -> fetchAndCache(uuid, uri));
        } catch (URISyntaxException | RuntimeException e) {
            plugin.getSLF4JLogger().debug("Could not get skin URL for {}", player.getName(), e);
        }
    }

    private void fetchAndCache(UUID uuid, URI skinUrl) {
        try {
            HttpRequest request = HttpRequest.newBuilder(skinUrl)
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();
            HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() != 200) {
                return;
            }
            byte[] body = response.body();
            if (body == null || body.length == 0) {
                return;
            }
            BufferedImage fullSkin = ImageIO.read(new java.io.ByteArrayInputStream(body));
            if (fullSkin == null || fullSkin.getWidth() < 16 || fullSkin.getHeight() < 16) {
                return;
            }
            BufferedImage face = extractFace(fullSkin);
            if (face != null) {
                long expiresAt = System.currentTimeMillis() + ttlSeconds * 1000L;
                cache.put(uuid, new CachedFace(face, expiresAt));
                plugin.getSLF4JLogger().debug("Cached skin face for {}", uuid);
            }
        } catch (Exception e) {
            plugin.getSLF4JLogger().debug("Failed to fetch skin for {}", uuid, e);
        }
    }

    /**
     * Extracts the 8×8 face region (pixels 8–15 on both axes). Same region for
     * classic and slim models.
     */
    private static BufferedImage extractFace(BufferedImage skin) {
        int w = skin.getWidth();
        int h = skin.getHeight();
        if (w < FACE_X + FACE_SIZE || h < FACE_Y + FACE_SIZE) {
            return null;
        }
        return skin.getSubimage(FACE_X, FACE_Y, FACE_SIZE, FACE_SIZE);
    }

    /**
     * Returns the cached 8×8 face image for the player, or empty if not cached.
     */
    public Optional<BufferedImage> getFace(UUID uuid) {
        CachedFace cached = cache.get(uuid);
        if (cached == null) {
            return Optional.empty();
        }
        if (System.currentTimeMillis() > cached.expiresAt) {
            cache.remove(uuid, cached);
            return Optional.empty();
        }
        return Optional.of(cached.face);
    }

    /**
     * Returns the bundled player placeholder image (used when skin not cached).
     */
    public BufferedImage getPlaceholder() {
        if (placeholder == null) {
            synchronized (this) {
                if (placeholder == null) {
                    try (InputStream is = plugin.getResource(PLACEHOLDER_RESOURCE)) {
                        if (is != null) {
                            placeholder = ImageIO.read(is);
                        }
                    } catch (IOException e) {
                        plugin.getSLF4JLogger().warn("Could not load player placeholder image", e);
                    }
                    if (placeholder == null) {
                        placeholder = new BufferedImage(FACE_SIZE, FACE_SIZE, BufferedImage.TYPE_INT_ARGB);
                    }
                }
            }
        }
        return placeholder;
    }

    private record CachedFace(BufferedImage face, long expiresAt) {}
}
