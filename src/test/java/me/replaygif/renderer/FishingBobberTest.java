package me.replaygif.renderer;

import me.replaygif.core.BlockRegistry;
import me.replaygif.core.EntitySnapshot;
import me.replaygif.core.WorldSnapshot;
import org.bukkit.entity.EntityType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;

import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * FB1 — FISHING_HOOK entity captured with shooterUUID = player UUID.
 * FB2 — fishing_bobber.png sprite drawn at hook position.
 * FB3 — Line drawn from player hand to bobber with downward curve.
 * FB4 — No line if shooter not in frame.
 */
class FishingBobberTest {

    @TempDir
    java.nio.file.Path tempDir;

    private static final int VOL = 32;
    private static final int TILE_WIDTH = 16;
    private static final int TILE_HEIGHT = 8;

    private BlockRegistry blockRegistry;
    private BlockColorMap blockColorMap;
    private EntitySpriteRegistry entitySpriteRegistry;
    private SkinCache skinCache;
    private IsometricRenderer renderer;

    @BeforeEach
    void setUp() throws Exception {
        blockRegistry = new BlockRegistry();
        InputStream defaults = getClass().getResourceAsStream("/block_colors_defaults.json");
        blockColorMap = new BlockColorMap(tempDir.toFile(), "block_colors.json", blockRegistry, defaults, null);

        var plugin = mock(org.bukkit.plugin.java.JavaPlugin.class);
        when(plugin.getSLF4JLogger()).thenReturn(LoggerFactory.getLogger(FishingBobberTest.class));
        when(plugin.getResource(anyString())).thenAnswer(inv -> {
            String path = inv.getArgument(0);
            var url = FishingBobberTest.class.getResource("/" + path);
            return url != null ? url.openStream() : null;
        });
        entitySpriteRegistry = new EntitySpriteRegistry(plugin, "");
        skinCache = new SkinCache(plugin, true, 3600);

        HurtParticleSynthesizer hurtSynth = new HurtParticleSynthesizer(TILE_WIDTH, TILE_HEIGHT);
        renderer = new IsometricRenderer(VOL, TILE_WIDTH, TILE_HEIGHT, 4, blockColorMap, blockRegistry,
                null, entitySpriteRegistry, skinCache, hurtSynth, null, null, null);
    }

    private static WorldSnapshot snapshotWithEntities(List<EntitySnapshot> entities) {
        return new WorldSnapshot(
                0L, 0, 0, 0, 0f, 0f, 20f, 20,
                "minecraft:overworld", "world",
                new short[VOL * VOL * VOL], VOL, entities, false);
    }

    /** FB1 — FISHING_HOOK entity with shooterUUID = player UUID. */
    @Test
    void fb1_fishingHookSnapshotHasShooterUuid() {
        UUID playerUuid = UUID.randomUUID();
        EntitySnapshot hook = new EntitySnapshot(
                EntityType.FISHING_HOOK,
                1, 0, 1, 0f, UUID.randomUUID(), false, false, false,
                null, 0.25, 0.25, 0f, false, -1.0f, null, null, playerUuid);
        assertNotNull(hook.shooterUUID);
        assertEquals(playerUuid, hook.shooterUUID);
    }

    /** FB2 — fishing_bobber.png sprite drawn at hook position. */
    @Test
    void fb2_fishingBobberSpriteDrawnAtHookPosition() {
        EntitySnapshot hook = new EntitySnapshot(
                EntityType.FISHING_HOOK,
                0, 0, 0, 0f, UUID.randomUUID(), false, false, false,
                null, 0.25, 0.25, 0f, false, -1.0f, null, null, null);

        assertTrue(entitySpriteRegistry.getSprite(EntityType.FISHING_HOOK).isPresent(),
                "FISHING_HOOK should resolve to fishing_bobber.png from registry");

        WorldSnapshot snapshot = snapshotWithEntities(List.of(hook));
        var context = new IsometricRenderer.RenderFrameContext(0, null, 0, 0, 0, false);
        BufferedImage img = renderer.renderFrame(snapshot, 0, context);
        assertNotNull(img);

        java.awt.Point hookScreen = renderer.project(0, 0, 0);
        int spriteW = (int) Math.round(0.25 * TILE_WIDTH);
        int spriteH = (int) Math.round(0.25 * TILE_HEIGHT * 2);
        int left = hookScreen.x - spriteW / 2;
        int top = (int) (hookScreen.y + TILE_HEIGHT - spriteH);
        int centerX = left + spriteW / 2;
        int centerY = top + spriteH / 2;

        int rgb = img.getRGB(
                Math.max(0, Math.min(centerX, img.getWidth() - 1)),
                Math.max(0, Math.min(centerY, img.getHeight() - 1)));
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;
        assertTrue(r > 150 || g > 150 || b > 150,
                "Hook position should show bobber sprite (red/white), not gray marker; got r=" + r + " g=" + g + " b=" + b);
    }

    /** FB3 — Line drawn from player hand to bobber with downward curve (#555555). */
    @Test
    void fb3_lineDrawnFromHandToBobberWithCurve() {
        UUID playerUuid = UUID.randomUUID();
        EntitySnapshot player = new EntitySnapshot(
                EntityType.PLAYER, 0, 0, 0, 0f, playerUuid, true, false, false,
                null, 0.6, 1.8);
        EntitySnapshot hook = new EntitySnapshot(
                EntityType.FISHING_HOOK,
                2, 0, 2, 0f, UUID.randomUUID(), false, false, false,
                null, 0.25, 0.25, 0f, false, -1.0f, null, null, playerUuid);

        WorldSnapshot snapshot = snapshotWithEntities(List.of(player, hook));
        var context = new IsometricRenderer.RenderFrameContext(0, null, 0, 0, 0, false);
        BufferedImage img = renderer.renderFrame(snapshot, 0, context);
        assertNotNull(img);

        java.awt.Point hand = renderer.project(0, 0, 0);
        int handX = hand.x + TILE_WIDTH / 4;
        int handY = (int) (hand.y + Math.round(1.8 * TILE_HEIGHT * 2) * 0.5);
        java.awt.Point bobber = renderer.project(2, 0, 2);
        int bobberX = bobber.x;
        int bobberY = bobber.y;
        double dx = bobberX - handX;
        double dy = bobberY - handY;
        double dist = Math.sqrt(dx * dx + dy * dy);
        double offset = dist * 0.1;
        int midX = (handX + bobberX) / 2;
        int midY = (int) ((handY + bobberY) / 2.0 + offset);

        int lineColor = 0x555555;
        int lineR = (lineColor >> 16) & 0xFF;
        int lineG = (lineColor >> 8) & 0xFF;
        int lineB = lineColor & 0xFF;

        int linePixels = 0;
        for (int x = Math.min(handX, Math.min(midX, bobberX)); x <= Math.max(handX, Math.max(midX, bobberX)) && linePixels < 3; x++) {
            if (x < 0 || x >= img.getWidth()) continue;
            for (int y = Math.min(handY, Math.min(midY, bobberY)); y <= Math.max(handY, Math.max(midY, bobberY)); y++) {
                if (y < 0 || y >= img.getHeight()) continue;
                int rgb = img.getRGB(x, y);
                int r = (rgb >> 16) & 0xFF;
                int g = (rgb >> 8) & 0xFF;
                int b = rgb & 0xFF;
                if (Math.abs(r - lineR) <= 10 && Math.abs(g - lineG) <= 10 && Math.abs(b - lineB) <= 10) {
                    linePixels++;
                    if (linePixels >= 3) break;
                }
            }
        }
        assertTrue(linePixels >= 3, "Expected at least 3 line pixels (#555555) along hand→mid→bobber; found " + linePixels);
    }

    /** FB4 — No line if shooter not in frame. */
    @Test
    void fb4_noLineWhenShooterNotInFrame() {
        UUID shooterUuidNotInFrame = UUID.randomUUID();
        EntitySnapshot hook = new EntitySnapshot(
                EntityType.FISHING_HOOK,
                0, 0, 0, 0f, UUID.randomUUID(), false, false, false,
                null, 0.25, 0.25, 0f, false, -1.0f, null, null, shooterUuidNotInFrame);

        WorldSnapshot snapshot = snapshotWithEntities(List.of(hook));
        var context = new IsometricRenderer.RenderFrameContext(0, null, 0, 0, 0, false);
        BufferedImage img = renderer.renderFrame(snapshot, 0, context);
        assertNotNull(img);

        java.awt.Point hookScreen = renderer.project(0, 0, 0);
        int lineColor = 0x555555;
        int lineR = (lineColor >> 16) & 0xFF;
        int lineG = (lineColor >> 8) & 0xFF;
        int lineB = lineColor & 0xFF;

        int linePixels = 0;
        for (int y = 0; y < img.getHeight(); y++) {
            for (int x = 0; x < img.getWidth(); x++) {
                int rgb = img.getRGB(x, y);
                int r = (rgb >> 16) & 0xFF;
                int g = (rgb >> 8) & 0xFF;
                int b = rgb & 0xFF;
                if (Math.abs(r - lineR) <= 5 && Math.abs(g - lineG) <= 5 && Math.abs(b - lineB) <= 5) {
                    linePixels++;
                }
            }
        }
        assertTrue(linePixels <= 2,
                "With shooter not in frame, line (#555555) should not be drawn; found " + linePixels + " matching pixels");
    }
}
