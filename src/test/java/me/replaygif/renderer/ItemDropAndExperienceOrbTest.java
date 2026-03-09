package me.replaygif.renderer;

import me.replaygif.core.BlockRegistry;
import me.replaygif.core.EntitySnapshot;
import me.replaygif.core.ItemSerializer;
import me.replaygif.core.WorldSnapshot;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.ItemStack;
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
 * IE1 — DROPPED_ITEM with DIAMOND_SWORD: EntitySnapshot has droppedItemMaterial = "DIAMOND_SWORD" or "DIAMOND_SWORD:enchanted".
 * IE2 — Rendered frame shows small item at entity position, not standard entity rectangle.
 * IE3 — Bob animation: sprite Y differs between frameIndex 0 and 4.
 * IE4 — EXPERIENCE_ORB: rendered as small green circle.
 * IE5 — Shadow ellipse drawn below both item and orb.
 */
class ItemDropAndExperienceOrbTest {

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
        when(plugin.getSLF4JLogger()).thenReturn(LoggerFactory.getLogger(ItemDropAndExperienceOrbTest.class));
        when(plugin.getResource(anyString())).thenAnswer(inv -> {
            String path = inv.getArgument(0);
            var url = ItemDropAndExperienceOrbTest.class.getResource("/" + path);
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

    /** IE1 — DROPPED_ITEM entity with DIAMOND_SWORD: EntitySnapshot has droppedItemMaterial = "DIAMOND_SWORD" or "DIAMOND_SWORD:enchanted". */
    @Test
    void ie1_droppedItemSnapshotHasMaterialAndSerializeFormat() {
        ItemStack plain = new ItemStack(Material.DIAMOND_SWORD);
        assertEquals("DIAMOND_SWORD", ItemSerializer.serialize(plain));

        ItemStack enchanted = new ItemStack(Material.DIAMOND_SWORD);
        enchanted.addUnsafeEnchantment(Enchantment.DURABILITY, 1);
        assertEquals("DIAMOND_SWORD:enchanted", ItemSerializer.serialize(enchanted));

        EntitySnapshot dropped = new EntitySnapshot(
                EntityType.DROPPED_ITEM,
                0, 0, 0, 0f, UUID.randomUUID(), false, false, false,
                null, 0.25, 0.25, 0f, false, -1.0f, null, "DIAMOND_SWORD", null);
        assertEquals("DIAMOND_SWORD", dropped.droppedItemMaterial);
    }

    /** IE2 — Rendered frame shows a small item sprite at entity position, not a standard entity rectangle. */
    @Test
    void ie2_droppedItemRendersAsSmallSprite() {
        EntitySnapshot dropped = new EntitySnapshot(
                EntityType.DROPPED_ITEM,
                0, 0, 0, 0f, UUID.randomUUID(), false, false, false,
                null, 0.25, 0.25, 0f, false, -1.0f, null, "DIAMOND_SWORD", null);

        WorldSnapshot snapshot = snapshotWithEntities(List.of(dropped));
        var context = new IsometricRenderer.RenderFrameContext(0, null, 0, 0, 0, false);
        BufferedImage img = renderer.renderFrame(snapshot, 0, context);

        int grayCount = countPixelsWithColor(img, 0x88, 0x88, 0x88);
        assertTrue(grayCount > 0, "Dropped item should render as small rectangle (fallback gray #888888) at entity position");
    }

    /** IE3 — Bob animation: sprite Y position differs between frameIndex 0 and frameIndex 4. */
    @Test
    void ie3_bobAnimationYDiffersBetweenFrames() {
        EntitySnapshot dropped = new EntitySnapshot(
                EntityType.DROPPED_ITEM,
                0, 0, 0, 0f, UUID.randomUUID(), false, false, false,
                null, 0.25, 0.25, 0f, false, -1.0f, null, "STONE", null);

        WorldSnapshot snapshot = snapshotWithEntities(List.of(dropped));
        var context = new IsometricRenderer.RenderFrameContext(0, null, 0, 0, 0, false);
        BufferedImage img0 = renderer.renderFrame(snapshot, 0, context);
        BufferedImage img4 = renderer.renderFrame(snapshot, 4, context);

        int centerX = renderer.getImageWidth() / 2;
        int y0 = firstGrayPixelColumnY(img0, centerX);
        int y4 = firstGrayPixelColumnY(img4, centerX);
        assertNotEquals(y0, y4, "Bob animation: item Y should differ between frame 0 and frame 4");
    }

    /** IE4 — EXPERIENCE_ORB: rendered as a small green circle (#7CFC00). */
    @Test
    void ie4_experienceOrbRendersAsGreenCircle() {
        EntitySnapshot orb = new EntitySnapshot(
                EntityType.EXPERIENCE_ORB,
                0, 0, 0, 0f, UUID.randomUUID(), false, false, false,
                null, 0.5, 0.5, 0f, false, -1.0f, null, null, null);

        WorldSnapshot snapshot = snapshotWithEntities(List.of(orb));
        var context = new IsometricRenderer.RenderFrameContext(0, null, 0, 0, 0, false);
        BufferedImage img = renderer.renderFrame(snapshot, 0, context);

        int greenCount = countPixelsWithColor(img, 0x7C, 0xFC, 0x00);
        assertTrue(greenCount > 0, "EXPERIENCE_ORB should render as green circle (#7CFC00)");
    }

    /** IE5 — Shadow ellipse drawn below both item and orb sprites. */
    @Test
    void ie5_shadowEllipseBelowItemAndOrb() {
        EntitySnapshot dropped = new EntitySnapshot(
                EntityType.DROPPED_ITEM,
                0, 0, 0, 0f, UUID.randomUUID(), false, false, false,
                null, 0.25, 0.25, 0f, false, -1.0f, null, "IRON_INGOT", null);

        WorldSnapshot snapshot = snapshotWithEntities(List.of(dropped));
        var context = new IsometricRenderer.RenderFrameContext(0, null, 0, 0, 0, false);
        BufferedImage img = renderer.renderFrame(snapshot, 0, context);

        java.awt.Point center = renderer.project(0, 0, 0);
        int shadowRegionY = center.y + (int) (TILE_HEIGHT * 0.5);
        int darkCount = 0;
        for (int dx = -TILE_WIDTH / 2; dx <= TILE_WIDTH / 2; dx++) {
            for (int dy = -2; dy <= 4; dy++) {
                int x = center.x + dx;
                int y = shadowRegionY + dy;
                if (x >= 0 && x < img.getWidth() && y >= 0 && y < img.getHeight()) {
                    int rgb = img.getRGB(x, y);
                    int r = (rgb >> 16) & 0xFF;
                    int g = (rgb >> 8) & 0xFF;
                    int b = rgb & 0xFF;
                    int a = (rgb >> 24) & 0xFF;
                    if (a > 0 && r < 80 && g < 80 && b < 80) darkCount++;
                }
            }
        }
        assertTrue(darkCount > 0, "Shadow ellipse (black at 30% opacity) should be drawn below dropped item");
    }

    private static int countPixelsWithColor(BufferedImage img, int r, int g, int b) {
        int count = 0;
        for (int y = 0; y < img.getHeight(); y++) {
            for (int x = 0; x < img.getWidth(); x++) {
                int rgb = img.getRGB(x, y);
                int pr = (rgb >> 16) & 0xFF;
                int pg = (rgb >> 8) & 0xFF;
                int pb = rgb & 0xFF;
                if (pr == r && pg == g && pb == b) count++;
            }
        }
        return count;
    }

    private static int firstGrayPixelColumnY(BufferedImage img, int centerX) {
        for (int y = 0; y < img.getHeight(); y++) {
            int rgb = img.getRGB(centerX, y);
            int r = (rgb >> 16) & 0xFF;
            int g = (rgb >> 8) & 0xFF;
            int b = rgb & 0xFF;
            if (r == 0x88 && g == 0x88 && b == 0x88) return y;
        }
        return -1;
    }
}
