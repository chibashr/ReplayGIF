package me.replaygif.renderer;

import me.replaygif.core.EntitySnapshot;
import me.replaygif.core.WorldSnapshot;
import org.bukkit.entity.EntityType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.awt.Point;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * HP1 — HurtRecord at hit frame; red dots only on that frame.
 * HP2 — DeathRecord at disappear frame; 12 dots in radial burst only on that frame.
 * HP3 — Determinism: same render twice produces identical particle positions.
 */
class HurtParticleSynthesizerTest {

    private static final int TILE_WIDTH = 16;
    private static final int TILE_HEIGHT = 8;
    private static final int VOL = 32;

    private HurtParticleSynthesizer synthesizer;

    @BeforeEach
    void setUp() {
        synthesizer = new HurtParticleSynthesizer(TILE_WIDTH, TILE_HEIGHT);
    }

    private static WorldSnapshot snapshotWithEntities(List<EntitySnapshot> entities) {
        return new WorldSnapshot(
                0L, 0, 0, 0, 0f, 0f, 20f, 20,
                "minecraft:overworld", "world",
                new short[VOL * VOL * VOL], VOL, entities, false);
    }

    /** HP1 — Entity hurtProgress goes 0→0.5 at frame 5. HurtRecord created at frame 5. 6 red dots drawn within entity bounds in frame 5. Not in frame 4 or 6. */
    @Test
    void hp1_hurtProgressTransition_createsHurtRecordAndRedDotsOnlyOnHitFrame() {
        UUID entityUuid = UUID.randomUUID();
        EntitySnapshot noHurt = new EntitySnapshot(
                EntityType.ZOMBIE, 0, 0, 0, 0f, entityUuid, false, false, false,
                null, 0.6, 1.95, 0f, false);
        EntitySnapshot hurt = new EntitySnapshot(
                EntityType.ZOMBIE, 0, 0, 0, 0f, entityUuid, false, false, false,
                null, 0.6, 1.95, 0.5f, false);

        List<WorldSnapshot> frames = List.of(
                snapshotWithEntities(List.of(noHurt)),
                snapshotWithEntities(List.of(noHurt)),
                snapshotWithEntities(List.of(noHurt)),
                snapshotWithEntities(List.of(noHurt)),
                snapshotWithEntities(List.of(noHurt)),
                snapshotWithEntities(List.of(hurt)),
                snapshotWithEntities(List.of(hurt)));

        synthesizer.analyze(frames);

        Map<Integer, List<HurtParticleSynthesizer.HurtRecord>> hurtByFrame = synthesizer.getHurtRecordsByFrame();
        assertNull(hurtByFrame.get(4), "No HurtRecord at frame 4");
        assertNotNull(hurtByFrame.get(5));
        assertEquals(1, hurtByFrame.get(5).size(), "One HurtRecord at frame 5");
        assertEquals(5, hurtByFrame.get(5).get(0).frameIndex());
        assertNull(hurtByFrame.get(6), "No HurtRecord at frame 6 (transition already happened)");

        HurtParticleSynthesizer.Projector project = (relX, relY, relZ) ->
                new Point(100 + (int) (relX - relZ) * (TILE_WIDTH / 2), 100 + (int) ((relX + relZ) * (TILE_HEIGHT / 2.0) - relY * TILE_HEIGHT));

        BufferedImage img4 = new BufferedImage(400, 400, BufferedImage.TYPE_INT_ARGB);
        synthesizer.draw(img4.createGraphics(), 4, project);
        int redCount4 = countRedPixels(img4);

        BufferedImage img5 = new BufferedImage(400, 400, BufferedImage.TYPE_INT_ARGB);
        synthesizer.draw(img5.createGraphics(), 5, project);
        int redCount5 = countRedPixels(img5);

        BufferedImage img6 = new BufferedImage(400, 400, BufferedImage.TYPE_INT_ARGB);
        synthesizer.draw(img6.createGraphics(), 6, project);
        int redCount6 = countRedPixels(img6);

        assertEquals(0, redCount4, "Frame 4: no red hurt dots");
        assertTrue(redCount5 >= 4 && redCount5 <= 6 * 4 * 4, "Frame 5: red dots (4–6 circles of ~2px radius)");
        assertEquals(0, redCount6, "Frame 6: no red hurt dots (only on hit frame)");
    }

    /** HP2 — Entity isDead transitions at frame 8. DeathRecord at frame 8. 12 dots in radial burst around last known position in frame 8 only. */
    @Test
    void hp2_isDeadTransition_createsDeathRecordAndBurstOnlyOnDisappearFrame() {
        UUID entityUuid = UUID.randomUUID();
        EntitySnapshot alive = new EntitySnapshot(
                EntityType.SKELETON, 1, 0, 1, 0f, entityUuid, false, false, false,
                null, 0.6, 1.99, 0f, false);

        List<WorldSnapshot> frames = List.of(
                snapshotWithEntities(List.of(alive)),
                snapshotWithEntities(List.of(alive)),
                snapshotWithEntities(List.of(alive)),
                snapshotWithEntities(List.of(alive)),
                snapshotWithEntities(List.of(alive)),
                snapshotWithEntities(List.of(alive)),
                snapshotWithEntities(List.of(alive)),
                snapshotWithEntities(List.of(alive)),
                snapshotWithEntities(List.of()));

        synthesizer.analyze(frames);

        Map<Integer, List<HurtParticleSynthesizer.DeathRecord>> deathByFrame = synthesizer.getDeathRecordsByFrame();
        assertNull(deathByFrame.get(7));
        assertNotNull(deathByFrame.get(8));
        assertEquals(1, deathByFrame.get(8).size());
        assertEquals(8, deathByFrame.get(8).get(0).disappearFrame());

        HurtParticleSynthesizer.Projector project = (relX, relY, relZ) ->
                new Point(200 + (int) (relX - relZ) * (TILE_WIDTH / 2), 200 + (int) ((relX + relZ) * (TILE_HEIGHT / 2.0) - relY * TILE_HEIGHT));

        BufferedImage img7 = new BufferedImage(500, 500, BufferedImage.TYPE_INT_ARGB);
        synthesizer.draw(img7.createGraphics(), 7, project);
        int deathColorCount7 = countDeathColorPixels(img7);

        BufferedImage img8 = new BufferedImage(500, 500, BufferedImage.TYPE_INT_ARGB);
        synthesizer.draw(img8.createGraphics(), 8, project);
        int deathColorCount8 = countDeathColorPixels(img8);

        assertEquals(0, deathColorCount7, "Frame 7: no death burst");
        assertTrue(deathColorCount8 >= 12 * 4, "Frame 8: 12 death dots (2px radius each)");
    }

    /** HP3 — Determinism: running the same render twice produces identical particle positions (seeded RNG). */
    @Test
    void hp3_determinism_sameRenderTwiceIdenticalPixels() {
        UUID entityUuid = UUID.nameUUIDFromBytes("test-entity".getBytes());
        EntitySnapshot noHurt = new EntitySnapshot(
                EntityType.CREEPER, 0, 0, 0, 0f, entityUuid, false, false, false,
                null, 0.6, 1.7, 0f, false);
        EntitySnapshot hurt = new EntitySnapshot(
                EntityType.CREEPER, 0, 0, 0, 0f, entityUuid, false, false, false,
                null, 0.6, 1.7, 0.3f, false);

        List<WorldSnapshot> frames = List.of(
                snapshotWithEntities(List.of(noHurt)),
                snapshotWithEntities(List.of(hurt)));

        synthesizer.analyze(frames);

        HurtParticleSynthesizer.Projector project = (relX, relY, relZ) ->
                new Point(150 + (int) (relX - relZ) * (TILE_WIDTH / 2), 150 + (int) ((relX + relZ) * (TILE_HEIGHT / 2.0) - relY * TILE_HEIGHT));

        BufferedImage img1 = new BufferedImage(400, 400, BufferedImage.TYPE_INT_ARGB);
        synthesizer.draw(img1.createGraphics(), 1, project);

        synthesizer.analyze(frames);
        BufferedImage img2 = new BufferedImage(400, 400, BufferedImage.TYPE_INT_ARGB);
        synthesizer.draw(img2.createGraphics(), 1, project);

        for (int y = 0; y < 400; y++) {
            for (int x = 0; x < 400; x++) {
                assertEquals(img1.getRGB(x, y), img2.getRGB(x, y),
                        "Pixel (" + x + "," + y + ") must match between two runs");
            }
        }
    }

    private static int countRedPixels(BufferedImage img) {
        int count = 0;
        for (int y = 0; y < img.getHeight(); y++) {
            for (int x = 0; x < img.getWidth(); x++) {
                int rgb = img.getRGB(x, y);
                int r = (rgb >> 16) & 0xFF;
                int g = (rgb >> 8) & 0xFF;
                int b = rgb & 0xFF;
                if (r == 255 && g == 0 && b == 0) count++;
            }
        }
        return count;
    }

    private static int countDeathColorPixels(BufferedImage img) {
        int count = 0;
        for (int y = 0; y < img.getHeight(); y++) {
            for (int x = 0; x < img.getWidth(); x++) {
                int rgb = img.getRGB(x, y);
                int r = (rgb >> 16) & 0xFF;
                int g = (rgb >> 8) & 0xFF;
                int b = rgb & 0xFF;
                if (r == 0x8B && g == 0x73 && b == 0x55) count++;
            }
        }
        return count;
    }
}
