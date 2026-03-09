package me.replaygif.renderer;

import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.profile.PlayerTextures;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
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
 * Caches player skin textures for rendering: full-body composite (head + body + limbs)
 * and face-only for compatibility. The entity pass draws the full body so players
 * appear as walking figures, not oversized faces. Fetch is triggered on join and
 * stored with TTL; getBody/getFace return empty until the async load completes.
 *
 * <p>Uses {@link org.bukkit.entity.Player#getPlayerProfile()} (available since 1.18); we do not
 * call {@code profile.complete()} so the main thread never blocks on profile lookup. On offline
 * mode or private profiles, textures may be null/empty or getSkin() null — we log DEBUG and
 * use the placeholder image.
 *
 * <p>{@link org.bukkit.profile.PlayerTextures#getSkin()} returns URL in 1.18 and 1.21; we
 * normalize to URI for HttpRequest and support either type at runtime for version skew.
 */
public class SkinCache {

    private static final String PLACEHOLDER_RESOURCE = "entity_sprites_default/player_placeholder.png";
    private static final int FACE_X = 8;
    private static final int FACE_Y = 8;
    private static final int FACE_SIZE = 8;
    private static final int OUT_WIDTH = 32;
    private static final int OUT_HEIGHT = 64;

    private final JavaPlugin plugin;
    private final boolean enabled;
    private final int ttlSeconds;
    private final ConcurrentHashMap<UUID, CachedSkin> cache = new ConcurrentHashMap<>();
    private volatile BufferedImage placeholder;
    private volatile BufferedImage placeholderBody;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    private final ExecutorService executor = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "ReplayGif-SkinCache");
        t.setDaemon(true);
        return t;
    });

    /**
     * @param plugin     for logger and placeholder resource
     * @param enabled    when false, onPlayerJoin and getFace do nothing / return empty
     * @param ttlSeconds cache expiry so skins are refreshed after a while
     */
    public SkinCache(JavaPlugin plugin, boolean enabled, int ttlSeconds) {
        this.plugin = plugin;
        this.enabled = enabled;
        this.ttlSeconds = ttlSeconds;
    }

    /** Schedules an async fetch of the player's skin face; no-op if disabled or no skin URL. Never blocks the main thread. */
    public void onPlayerJoin(Player player) {
        if (!enabled) {
            return;
        }
        PlayerTextures textures;
        try {
            textures = player.getPlayerProfile().getTextures();
        } catch (RuntimeException e) {
            plugin.getSLF4JLogger().debug("No textures for {} (incomplete profile or offline); using placeholder", player.getName());
            return;
        }
        if (textures == null || textures.isEmpty()) {
            plugin.getSLF4JLogger().debug("No skin URL for {} (private/offline profile); using placeholder", player.getName());
            return;
        }
        Object skinRef = textures.getSkin();
        if (skinRef == null) {
            plugin.getSLF4JLogger().debug("No skin URL for {} (offline or no texture); using placeholder", player.getName());
            return;
        }
        URI uri = toUri(skinRef);
        if (uri == null) {
            plugin.getSLF4JLogger().debug("Could not convert skin reference for {} to URI; using placeholder", player.getName());
            return;
        }
        UUID uuid = player.getUniqueId();
        executor.submit(() -> fetchAndCache(uuid, uri));
    }

    /** Converts getSkin() result to URI; getSkin() returns URL in 1.18/1.21, possibly URI in other builds. */
    private static URI toUri(Object skinRef) {
        if (skinRef == null) {
            return null;
        }
        try {
            if (skinRef instanceof java.net.URL) {
                return ((java.net.URL) skinRef).toURI();
            }
            if (skinRef instanceof URI) {
                return (URI) skinRef;
            }
        } catch (URISyntaxException e) {
            return null;
        }
        return null;
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
            BufferedImage bodyImage = extractFullBody(fullSkin);
            if (face != null || bodyImage != null) {
                long expiresAt = System.currentTimeMillis() + ttlSeconds * 1000L;
                cache.put(uuid, new CachedSkin(face, bodyImage, expiresAt));
                plugin.getSLF4JLogger().debug("Cached skin for {}", uuid);
            }
        } catch (Exception e) {
            plugin.getSLF4JLogger().debug("Failed to fetch skin for {}", uuid, e);
        }
    }

    /** Face region is 8–15 on both axes for both classic and slim skin layouts. */
    private static BufferedImage extractFace(BufferedImage skin) {
        int w = skin.getWidth();
        int h = skin.getHeight();
        if (w < FACE_X + FACE_SIZE || h < FACE_Y + FACE_SIZE) {
            return null;
        }
        return skin.getSubimage(FACE_X, FACE_Y, FACE_SIZE, FACE_SIZE);
    }

    /** Full-body composite: head, body, arms, legs from 64×64 skin (classic layout). */
    private static BufferedImage extractFullBody(BufferedImage skin) {
        int w = skin.getWidth();
        int h = skin.getHeight();
        if (w < 48 || h < 64) {
            return null;
        }
        BufferedImage out = new BufferedImage(OUT_WIDTH, OUT_HEIGHT, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        try {
            g.drawImage(skin.getSubimage(FACE_X, FACE_Y, FACE_SIZE, FACE_SIZE), 8, 0, 16, 16, null);
            g.drawImage(skin.getSubimage(20, 20, 8, 12), 8, 16, 16, 24, null);
            g.drawImage(skin.getSubimage(44, 20, 4, 12), 4, 16, 8, 24, null);
            g.drawImage(skin.getSubimage(36, 52, 4, 12), 20, 16, 8, 24, null);
            g.drawImage(skin.getSubimage(4, 20, 4, 12), 8, 40, 8, 24, null);
            g.drawImage(skin.getSubimage(20, 52, 4, 12), 16, 40, 8, 24, null);
        } finally {
            g.dispose();
        }
        return out;
    }

    /** Cached face or empty if not yet loaded or expired. */
    public Optional<BufferedImage> getFace(UUID uuid) {
        CachedSkin cached = cache.get(uuid);
        if (cached == null) {
            return Optional.empty();
        }
        if (System.currentTimeMillis() > cached.expiresAt) {
            cache.remove(uuid, cached);
            return Optional.empty();
        }
        return Optional.ofNullable(cached.face);
    }

    /** Full-body composite; empty if not loaded or expired. Renderer uses this for player sprites. */
    public Optional<BufferedImage> getBody(UUID uuid) {
        CachedSkin cached = cache.get(uuid);
        if (cached == null) {
            return Optional.empty();
        }
        if (System.currentTimeMillis() > cached.expiresAt) {
            cache.remove(uuid, cached);
            return Optional.empty();
        }
        return Optional.ofNullable(cached.body);
    }

    /** Fallback when getFace returns empty; lazy-loaded from resources. */
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

    /** Full-body fallback when getBody returns empty; generic humanoid shape. */
    public BufferedImage getPlaceholderBody() {
        if (placeholderBody == null) {
            synchronized (this) {
                if (placeholderBody == null) {
                    placeholderBody = createPlaceholderBody();
                }
            }
        }
        return placeholderBody;
    }

    private BufferedImage createPlaceholderBody() {
        BufferedImage face = getPlaceholder();
        BufferedImage out = new BufferedImage(OUT_WIDTH, OUT_HEIGHT, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        try {
            g.drawImage(face, 8, 0, 16, 16, null);
            g.setColor(new java.awt.Color(0x72, 0x8B, 0x9A));
            g.fillRect(8, 16, 16, 24);
            g.fillRect(8, 40, 8, 24);
            g.fillRect(16, 40, 8, 24);
        } finally {
            g.dispose();
        }
        return out;
    }

    /** Stops the fetch executor; call from plugin onDisable to avoid leaks. */
    public void shutdown() {
        executor.shutdownNow();
    }

    private record CachedSkin(BufferedImage face, BufferedImage body, long expiresAt) {}
}
