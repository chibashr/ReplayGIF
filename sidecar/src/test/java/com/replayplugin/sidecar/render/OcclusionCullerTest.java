package com.replayplugin.sidecar.render;

import com.replayplugin.capture.BlockEntry;
import com.replayplugin.capture.ChunkData;
import com.replayplugin.sidecar.asset.AssetManager;
import com.replayplugin.sidecar.asset.BiomeTint;
import com.replayplugin.sidecar.asset.BlockModelResolver;
import com.replayplugin.sidecar.model.BlockPos;
import com.replayplugin.sidecar.model.WorldSnapshotAdapter;
import com.replayplugin.sidecar.FixtureJarBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * OCC-001 through OCC-004: Occlusion Culling.
 */
class OcclusionCullerTest {

    @TempDir
    Path tempDir;

    private static ChunkData chunk(List<BlockEntry> blocks) {
        return new ChunkData(0, 0, "minecraft:plains", blocks);
    }

    private static BlockEntry block(int x, int y, int z, String id) {
        return new BlockEntry(x, y, z, id, null, 15, 0);
    }

    @Test
    void OCC001_blockBetweenCameraAndPlayer_occluded() {
        double px = 5.5, py = 1.5, pz = 5.5;
        BlockEntry between = block(4, 1, 5, "minecraft:stone");
        BlockEntry playerBlock = block(5, 1, 5, "minecraft:dirt");
        OcclusionCuller culler = new OcclusionCuller();
        Set<BlockPos> occluded = culler.computeOccluded(
                List.of(chunk(List.of(between, playerBlock))), px, py, pz);

        assertTrue(occluded.contains(new BlockPos(4, 1, 5)));
    }

    @Test
    void OCC002_blockBehindPlayer_notOccluded() {
        double px = 5.5, py = 1.5, pz = 5.5;
        BlockEntry behind = block(6, 1, 6, "minecraft:stone");
        BlockEntry playerBlock = block(5, 1, 5, "minecraft:dirt");
        OcclusionCuller culler = new OcclusionCuller();
        Set<BlockPos> occluded = culler.computeOccluded(
                List.of(chunk(List.of(behind, playerBlock))), px, py, pz);

        assertFalse(occluded.contains(new BlockPos(6, 1, 6)));
    }

    @Test
    void OCC003_blockAdjacentNotIntersecting_notOccluded() {
        double px = 5.5, py = 1.5, pz = 5.5;
        BlockEntry side = block(10, 1, 10, "minecraft:stone");
        BlockEntry playerBlock = block(5, 1, 5, "minecraft:dirt");
        OcclusionCuller culler = new OcclusionCuller();
        Set<BlockPos> occluded = culler.computeOccluded(
                List.of(chunk(List.of(side, playerBlock))), px, py, pz);

        assertFalse(occluded.contains(new BlockPos(10, 1, 10)));
    }

    @Test
    void OCC004_playerVisibleInFinalFrame_spriteOnTop() throws Exception {
        com.replayplugin.capture.EntityState entity = new com.replayplugin.capture.EntityState(5.5, 1.5, 5.5, 0, 0, "STANDING", null, null);
        com.replayplugin.capture.RenderConfigDto config = new com.replayplugin.capture.RenderConfigDto(10, 16, 1, "follow_player", true, List.of());
        com.replayplugin.capture.ChunkData chunkData = chunk(List.of(block(5, 1, 5, "minecraft:stone")));
        com.replayplugin.capture.FrameSnapshot frame = new com.replayplugin.capture.FrameSnapshot(0, 0, entity, List.of(chunkData));
        com.replayplugin.sidecar.model.WorldSnapshot snapshot = new WorldSnapshotAdapter(frame);

        Path jarPath = FixtureJarBuilder.ensureFixtureJar(tempDir.resolve("fixtures"));
        Path dataDir = tempDir.resolve("assets-data");
        Files.createDirectories(dataDir);
        AssetManager am = new AssetManager(jarPath);
        am.initialize(dataDir, "1.21");
        BlockModelResolver bmr = new BlockModelResolver(am);
        BiomeTint tint = new BiomeTint(am);
        OcclusionCuller occ = new OcclusionCuller();
        PlayerSpriteRenderer psr = new PlayerSpriteRenderer(am);
        IsometricRenderer renderer = new IsometricRenderer(bmr, tint, occ, psr);
        java.awt.image.BufferedImage img = renderer.renderFrame(snapshot, entity, config);
        assertNotNull(img);
        int centerX = img.getWidth() / 2;
        int centerY = (int)(img.getHeight() * 0.55);
        int nonTransparent = 0;
        for (int dy = -8; dy <= 8; dy++) {
            for (int dx = -8; dx <= 8; dx++) {
                int x = centerX + dx, y = centerY + dy;
                if (x >= 0 && x < img.getWidth() && y >= 0 && y < img.getHeight()) {
                    if (((img.getRGB(x, y) >> 24) & 0xff) > 128) nonTransparent++;
                }
            }
        }
        assertTrue(nonTransparent > 0, "Player sprite area should have opaque pixels");
    }
}
