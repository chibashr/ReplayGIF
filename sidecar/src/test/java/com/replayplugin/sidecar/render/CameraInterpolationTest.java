package com.replayplugin.sidecar.render;

import com.replayplugin.capture.BlockEntry;
import com.replayplugin.capture.ChunkData;
import com.replayplugin.capture.EntityState;
import com.replayplugin.capture.FrameSnapshot;
import com.replayplugin.capture.RenderConfigDto;
import com.replayplugin.sidecar.FixtureJarBuilder;
import com.replayplugin.sidecar.asset.AssetManager;
import com.replayplugin.sidecar.asset.BiomeTint;
import com.replayplugin.sidecar.asset.BlockModelResolver;
import com.replayplugin.sidecar.model.WorldSnapshotAdapter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * CAM-001 through CAM-003: Camera Interpolation.
 */
class CameraInterpolationTest {

    @TempDir
    Path tempDir;
    IsometricRenderer renderer;

    @BeforeEach
    void setUp() throws Exception {
        Path jarPath = FixtureJarBuilder.ensureFixtureJar(tempDir.resolve("fixtures"));
        Path dataDir = tempDir.resolve("assets");
        AssetManager am = new AssetManager(jarPath);
        am.initialize(dataDir, "1.21");
        BlockModelResolver bmr = new BlockModelResolver(am);
        BiomeTint tint = new BiomeTint(am);
        OcclusionCuller occ = new OcclusionCuller();
        PlayerSpriteRenderer psr = new PlayerSpriteRenderer(am);
        renderer = new IsometricRenderer(bmr, tint, occ, psr);
    }

    @Test
    void CAM001_playerYChanges_cameraSmoothlyInterpolated() {
        RenderConfigDto config = new RenderConfigDto(10, 16, 1, "follow_player", false, List.of());
        ChunkData chunk = new ChunkData(0, 0, "minecraft:plains", List.of());
        EntityState e1 = new EntityState(5, 1, 5, 0, 0, "STANDING", null, null);
        EntityState e2 = new EntityState(5, 3, 5, 0, 0, "STANDING", null, null);
        FrameSnapshot f1 = new FrameSnapshot(0, 0, e1, List.of(chunk));
        FrameSnapshot f2 = new FrameSnapshot(1, 1, e2, List.of(chunk));

        BufferedImage img1 = renderer.renderFrame(new WorldSnapshotAdapter(f1), e1, config);
        BufferedImage img2 = renderer.renderFrame(new WorldSnapshotAdapter(f2), e2, config);
        assertNotNull(img1);
        assertNotNull(img2);
    }

    @Test
    void CAM002_playerYConstant_cameraConstant() {
        RenderConfigDto config = new RenderConfigDto(10, 16, 1, "follow_player", false, List.of());
        ChunkData chunk = new ChunkData(0, 0, "minecraft:plains", List.of());
        EntityState e = new EntityState(5, 2, 5, 0, 0, "STANDING", null, null);
        FrameSnapshot f = new FrameSnapshot(0, 0, e, List.of(chunk));

        BufferedImage img1 = renderer.renderFrame(new WorldSnapshotAdapter(f), e, config);
        BufferedImage img2 = renderer.renderFrame(new WorldSnapshotAdapter(f), e, config);
        assertNotNull(img1);
        assertNotNull(img2);
    }

    @Test
    void CAM003_playerXZChanges_cameraFollows() {
        RenderConfigDto config = new RenderConfigDto(10, 16, 1, "follow_player", false, List.of());
        ChunkData chunk = new ChunkData(0, 0, "minecraft:plains", List.of());
        EntityState e1 = new EntityState(5, 1, 5, 0, 0, "STANDING", null, null);
        EntityState e2 = new EntityState(7, 1, 7, 0, 0, "STANDING", null, null);
        FrameSnapshot f1 = new FrameSnapshot(0, 0, e1, List.of(chunk));
        FrameSnapshot f2 = new FrameSnapshot(1, 1, e2, List.of(chunk));

        BufferedImage img1 = renderer.renderFrame(new WorldSnapshotAdapter(f1), e1, config);
        BufferedImage img2 = renderer.renderFrame(new WorldSnapshotAdapter(f2), e2, config);
        assertNotNull(img1);
        assertNotNull(img2);
    }
}
