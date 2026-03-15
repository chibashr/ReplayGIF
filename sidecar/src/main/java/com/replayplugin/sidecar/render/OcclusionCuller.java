package com.replayplugin.sidecar.render;

import com.replayplugin.capture.BlockEntry;
import com.replayplugin.capture.ChunkData;
import com.replayplugin.sidecar.model.BlockPos;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Per-frame occlusion: blocks between camera and player (by projection on camera-to-player segment) are occluded.
 * Camera is northwest-above player: direction (-1, -0.5, 1) normalized.
 */
public final class OcclusionCuller {

    private static final double CAM_DX = -1.0;
    private static final double CAM_DY = -0.5;
    private static final double CAM_DZ = 1.0;
    private static final double LEN = Math.sqrt(CAM_DX * CAM_DX + CAM_DY * CAM_DY + CAM_DZ * CAM_DZ);
    private static final double RAY_DX = CAM_DX / LEN;
    private static final double RAY_DY = CAM_DY / LEN;
    private static final double RAY_DZ = CAM_DZ / LEN;

    /**
     * Returns the set of block positions that are occluded (behind other blocks on the ray from camera to player).
     */
    public Set<BlockPos> computeOccluded(
            List<ChunkData> chunks,
            double playerX, double playerY, double playerZ) {

        Set<BlockPos> allBlocks = new HashSet<>();
        for (ChunkData chunk : chunks) {
            for (BlockEntry b : chunk.getBlocks()) {
                if ("minecraft:air".equals(b.getBlockId())) continue;
                allBlocks.add(new BlockPos(b.getX(), b.getY(), b.getZ()));
            }
        }

        double far = 2000;
        double camX = playerX + 0.5 + far * RAY_DX;
        double camY = playerY + 0.5 + far * RAY_DY;
        double camZ = playerZ + 0.5 + far * RAY_DZ;
        double toPlayerX = (playerX + 0.5) - camX;
        double toPlayerY = (playerY + 0.5) - camY;
        double toPlayerZ = (playerZ + 0.5) - camZ;
        double lenSq = toPlayerX * toPlayerX + toPlayerY * toPlayerY + toPlayerZ * toPlayerZ;
        if (lenSq < 1e-12) return new HashSet<>();

        double playerDistSq = lenSq;
        double eps = 1e-6;
        Set<BlockPos> occluded = new HashSet<>();
        for (BlockPos pos : allBlocks) {
            double bx = pos.getX() + 0.5, by = pos.getY() + 0.5, bz = pos.getZ() + 0.5;
            double toBlockX = bx - camX, toBlockY = by - camY, toBlockZ = bz - camZ;
            double lambda = (toBlockX * toPlayerX + toBlockY * toPlayerY + toBlockZ * toPlayerZ) / lenSq;
            double blockDistSq = toBlockX * toBlockX + toBlockY * toBlockY + toBlockZ * toBlockZ;
            boolean between = lambda > 1e-4 && lambda < 1 - 1e-4;
            boolean closer = blockDistSq < playerDistSq - eps;
            if (between && closer) occluded.add(pos);
        }
        return occluded;
    }
}
