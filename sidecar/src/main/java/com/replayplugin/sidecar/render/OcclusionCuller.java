package com.replayplugin.sidecar.render;

import com.replayplugin.capture.BlockEntry;
import com.replayplugin.capture.ChunkData;
import com.replayplugin.sidecar.model.BlockPos;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Per-frame occlusion: cast ray from camera (northwest-above) toward player.
 * If ray passes through a block's AABB, mark that block occluded.
 * Camera direction: normalize(-1, -0.5, 1).
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
        double rayOx = playerX + 0.5 + far * RAY_DX;
        double rayOy = playerY + 0.5 + far * RAY_DY;
        double rayOz = playerZ + 0.5 + far * RAY_DZ;
        double rayDx = -RAY_DX;
        double rayDy = -RAY_DY;
        double rayDz = -RAY_DZ;

        double playerCx = playerX + 0.5;
        double playerCy = playerY + 0.5;
        double playerCz = playerZ + 0.5;
        double tPlayer = rayDx != 0 ? (playerCx - rayOx) / rayDx
                : rayDy != 0 ? (playerCy - rayOy) / rayDy
                : (playerCz - rayOz) / rayDz;

        Set<BlockPos> occluded = new HashSet<>();
        for (BlockPos pos : allBlocks) {
            double t = rayAABBEntry(rayOx, rayOy, rayOz, rayDx, rayDy, rayDz,
                    pos.getX(), pos.getY(), pos.getZ(), 1, 1, 1);
            if (t >= 0 && t < tPlayer) occluded.add(pos);
        }
        return occluded;
    }

    private static double rayAABBEntry(double ox, double oy, double oz, double dx, double dy, double dz,
                                      double bx, double by, double bz, double bw, double bh, double bd) {
        double invDx = dx == 0 ? Double.POSITIVE_INFINITY : 1.0 / dx;
        double invDy = dy == 0 ? Double.POSITIVE_INFINITY : 1.0 / dy;
        double invDz = dz == 0 ? Double.POSITIVE_INFINITY : 1.0 / dz;
        double tMinX = ((bx - ox) * invDx);
        double tMaxX = ((bx + bw - ox) * invDx);
        if (invDx < 0) { double tmp = tMinX; tMinX = tMaxX; tMaxX = tmp; }
        double tMinY = ((by - oy) * invDy);
        double tMaxY = ((by + bh - oy) * invDy);
        if (invDy < 0) { double tmp = tMinY; tMinY = tMaxY; tMaxY = tmp; }
        double tMinZ = ((bz - oz) * invDz);
        double tMaxZ = ((bz + bd - oz) * invDz);
        if (invDz < 0) { double tmp = tMinZ; tMinZ = tMaxZ; tMaxZ = tmp; }
        double tEntry = Math.max(tMinX, Math.max(tMinY, tMinZ));
        double tExit = Math.min(tMaxX, Math.min(tMaxY, tMaxZ));
        if (tEntry <= tExit && tExit >= 0) return tEntry >= 0 ? tEntry : 0;
        return -1;
    }
}
