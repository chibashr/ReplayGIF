package com.replayplugin.sidecar.render;

import com.replayplugin.capture.BlockEntry;
import com.replayplugin.capture.ChunkData;
import com.replayplugin.capture.EntityState;
import com.replayplugin.capture.RenderConfigDto;
import com.replayplugin.sidecar.asset.BiomeTint;
import com.replayplugin.sidecar.asset.BlockGeometry;
import com.replayplugin.sidecar.asset.BlockModelResolver;
import com.replayplugin.sidecar.model.BlockPos;
import com.replayplugin.sidecar.model.WorldSnapshot;

import java.awt.*;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Renders a single frame: blocks in isometric order, then player sprite. Camera Y uses dampened lerp.
 */
public final class IsometricRenderer {

    private static final double CAM_LERP_FACTOR = 0.2;

    private final BlockModelResolver blockModelResolver;
    private final BiomeTint biomeTint;
    private final OcclusionCuller occlusionCuller;
    private final PlayerSpriteRenderer playerSpriteRenderer;

    private double previousCameraY = Double.NaN;

    public IsometricRenderer(BlockModelResolver blockModelResolver, BiomeTint biomeTint,
                            OcclusionCuller occlusionCuller, PlayerSpriteRenderer playerSpriteRenderer) {
        this.blockModelResolver = blockModelResolver;
        this.biomeTint = biomeTint;
        this.occlusionCuller = occlusionCuller;
        this.playerSpriteRenderer = playerSpriteRenderer;
    }

    /**
     * Renders one frame. Contract: IsometricRenderer.renderFrame(WorldSnapshot, EntityState, RenderConfig) → BufferedImage.
     */
    public BufferedImage renderFrame(WorldSnapshot snapshot, EntityState entity, RenderConfigDto config) {
        int ppb = config.getPixelsPerBlock();
        int radiusChunks = config.getCaptureRadiusChunks();
        int chunkDiameter = radiusChunks * 2 + 1;
        int worldExtent = chunkDiameter * 16;
        int canvasW = (int) Math.ceil(worldExtent * Math.sqrt(2) * ppb);
        int canvasH = (int) Math.ceil(worldExtent * 1.5 * ppb);

        double targetCameraY = entity.getY() + 0.5;
        double cameraY = Double.isNaN(previousCameraY) ? targetCameraY : previousCameraY + CAM_LERP_FACTOR * (targetCameraY - previousCameraY);
        previousCameraY = cameraY;

        BufferedImage canvas = new BufferedImage(canvasW, canvasH, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = canvas.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);

        List<ChunkData> chunks = snapshot.getChunks();
        Set<BlockPos> occluded = config.isCutoutEnabled()
                ? occlusionCuller.computeOccluded(chunks, entity.getX(), entity.getY(), entity.getZ())
                : Set.of();

        List<BlockDraw> draws = new ArrayList<>();
        for (ChunkData chunk : chunks) {
            String biome = chunk.getBiome();
            for (BlockEntry block : chunk.getBlocks()) {
                if ("minecraft:air".equals(block.getBlockId())) continue;
                BlockPos pos = new BlockPos(block.getX(), block.getY(), block.getZ());
                if (occluded.contains(pos)) continue;
                BlockGeometry geometry = blockModelResolver.resolve(block.getBlockId(), block.getBlockStateProperties());
                int bx = block.getX(), by = block.getY(), bz = block.getZ();
                for (BlockGeometry.Element el : geometry.getElements()) {
                    double[] from = el.getFrom();
                    double[] to = el.getTo();
                    for (var faceEntry : el.getFaces().entrySet()) {
                        String faceName = faceEntry.getKey();
                        BlockGeometry.FaceInfo faceInfo = faceEntry.getValue();
                        BufferedImage tex = geometry.getTextures().get(faceInfo.getTexture());
                        if (tex == null) continue;
                        tex = LightingApplier.apply(tex, faceName);
                        if (geometry.isRequiresBiomeTint()) {
                            try {
                                BiomeTint.TintType type = blockIdToTintType(block.getBlockId());
                                Color tint = biomeTint.getTint(biome, type);
                                tex = applyTint(tex, tint);
                            } catch (Exception ignored) {
                            }
                        }
                        double[][] verts = faceVerts(faceName, from, to);
                        int[] sx = new int[4];
                        int[] sy = new int[4];
                        for (int i = 0; i < 4; i++) {
                            double wx = bx + verts[i][0] / 16.0;
                            double wy = by + verts[i][1] / 16.0;
                            double wz = bz + verts[i][2] / 16.0;
                            java.awt.geom.Point2D p = IsoProjection.project(wx, wy, wz, ppb);
                            sx[i] = (int) p.getX();
                            sy[i] = (int) p.getY();
                        }
                        draws.add(new BlockDraw(sx, sy, tex, faceInfo.getUv()));
                    }
                }
            }
        }

        java.awt.geom.Point2D centerProj = IsoProjection.project(entity.getX() + 0.5, cameraY, entity.getZ() + 0.5, ppb);
        double originX = canvasW / 2.0 - centerProj.getX();
        double originY = canvasH * 0.55 - centerProj.getY();

        for (BlockDraw d : draws) {
            for (int i = 0; i < 4; i++) {
                d.sx[i] += (int) originX;
                d.sy[i] += (int) originY;
            }
        }

        draws.sort((a, b) -> {
            int maxSyA = Math.max(Math.max(a.sy[0], a.sy[1]), Math.max(a.sy[2], a.sy[3]));
            int maxSyB = Math.max(Math.max(b.sy[0], b.sy[1]), Math.max(b.sy[2], b.sy[3]));
            return Integer.compare(maxSyA, maxSyB);
        });

        for (BlockDraw d : draws) {
            drawTexturedQuad(g, d);
        }

        java.awt.geom.Point2D playerScreen = IsoProjection.project(entity.getX() + 0.5, entity.getY() + 0.5, entity.getZ() + 0.5, ppb);
        int playerPx = (int) (playerScreen.getX() + originX);
        int playerPy = (int) (playerScreen.getY() + originY);

        BufferedImage sprite = playerSpriteRenderer.render(entity, ppb);
        int spriteW = sprite.getWidth();
        int spriteH = sprite.getHeight();
        g.drawImage(sprite, playerPx - spriteW / 2, playerPy - spriteH, spriteW, spriteH, null);

        g.dispose();
        return canvas;
    }

    private static double[][] faceVerts(String faceName, double[] from, double[] to) {
        double x0 = from[0], y0 = from[1], z0 = from[2];
        double x1 = to[0], y1 = to[1], z1 = to[2];
        return switch (faceName) {
            case "up" -> new double[][]{{x0, y1, z0}, {x1, y1, z0}, {x1, y1, z1}, {x0, y1, z1}};
            case "down" -> new double[][]{{x0, y0, z0}, {x0, y0, z1}, {x1, y0, z1}, {x1, y0, z0}};
            case "north" -> new double[][]{{x0, y0, z0}, {x1, y0, z0}, {x1, y1, z0}, {x0, y1, z0}};
            case "south" -> new double[][]{{x0, y0, z1}, {x0, y1, z1}, {x1, y1, z1}, {x1, y0, z1}};
            case "east" -> new double[][]{{x1, y0, z0}, {x1, y0, z1}, {x1, y1, z1}, {x1, y1, z0}};
            case "west" -> new double[][]{{x0, y0, z0}, {x0, y1, z0}, {x0, y1, z1}, {x0, y0, z1}};
            default -> new double[][]{{x0, y0, z0}, {x1, y0, z0}, {x1, y1, z0}, {x0, y1, z0}};
        };
    }

    private static void drawTexturedQuad(Graphics2D g, BlockDraw d) {
        if (d.tex == null) return;
        int w = d.tex.getWidth();
        int h = d.tex.getHeight();
        double[] uv = d.uv;
        int tw = w, th = h;
        int u0 = 0, v0 = 0, u1 = w, v1 = h;
        if (uv != null && uv.length >= 4) {
            u0 = (int) (uv[0] / 16.0 * w);
            v0 = (int) (uv[1] / 16.0 * h);
            u1 = (int) (uv[2] / 16.0 * w);
            v1 = (int) (uv[3] / 16.0 * h);
            tw = Math.max(1, u1 - u0);
            th = Math.max(1, v1 - v0);
        }
        Path2D path = new Path2D.Double();
        path.moveTo(d.sx[0], d.sy[0]);
        path.lineTo(d.sx[1], d.sy[1]);
        path.lineTo(d.sx[2], d.sy[2]);
        path.lineTo(d.sx[3], d.sy[3]);
        path.closePath();
        Shape clip = g.getClip();
        g.setClip(path);
        int minSx = Math.min(Math.min(d.sx[0], d.sx[1]), Math.min(d.sx[2], d.sx[3]));
        int minSy = Math.min(Math.min(d.sy[0], d.sy[1]), Math.min(d.sy[2], d.sy[3]));
        int maxSx = Math.max(Math.max(d.sx[0], d.sx[1]), Math.max(d.sx[2], d.sx[3]));
        int maxSy = Math.max(Math.max(d.sy[0], d.sy[1]), Math.max(d.sy[2], d.sy[3]));
        g.drawImage(d.tex, minSx, minSy, maxSx - minSx, maxSy - minSy, u0, v0, u1, v1, null);
        g.setClip(clip);
    }

    private static BiomeTint.TintType blockIdToTintType(String blockId) {
        if (blockId == null) return BiomeTint.TintType.GRASS;
        if (blockId.contains("water") || blockId.contains("cauldron")) return BiomeTint.TintType.WATER;
        if (blockId.contains("leaves") || blockId.contains("grass") && !blockId.contains("block")) return BiomeTint.TintType.FOLIAGE;
        return BiomeTint.TintType.GRASS;
    }

    private static BufferedImage applyTint(BufferedImage img, Color tint) {
        int w = img.getWidth();
        int h = img.getHeight();
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        float tr = tint.getRed() / 255f, tg = tint.getGreen() / 255f, tb = tint.getBlue() / 255f;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int rgb = img.getRGB(x, y);
                int a = (rgb >> 24) & 0xff;
                int r = (int) (((rgb >> 16) & 0xff) * tr);
                int g = (int) (((rgb >> 8) & 0xff) * tg);
                int b = (int) ((rgb & 0xff) * tb);
                r = Math.max(0, Math.min(255, r));
                g = Math.max(0, Math.min(255, g));
                b = Math.max(0, Math.min(255, b));
                out.setRGB(x, y, (a << 24) | (r << 16) | (g << 8) | b);
            }
        }
        return out;
    }

    private static final class BlockDraw {
        final int[] sx, sy;
        final BufferedImage tex;
        final double[] uv;

        BlockDraw(int[] sx, int[] sy, BufferedImage tex, double[] uv) {
            this.sx = sx;
            this.sy = sy;
            this.tex = tex;
            this.uv = uv;
        }
    }
}
