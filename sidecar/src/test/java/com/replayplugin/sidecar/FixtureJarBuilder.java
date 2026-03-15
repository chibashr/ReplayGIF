package com.replayplugin.sidecar;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Programmatically builds a minimal Minecraft client fixture JAR for tests.
 * Contains block textures (16×16 solid-color stubs), blockstates, models, and colormaps.
 * Grass/foliage colormaps use gradient data for tint tests.
 */
public final class FixtureJarBuilder {

    private static final String PREFIX = "assets/minecraft/";
    private static final String TEXTURES_BLOCK = PREFIX + "textures/block/";
    private static final String BLOCKSTATES = PREFIX + "blockstates/";
    private static final String MODELS_BLOCK = PREFIX + "models/block/";
    private static final String COLORMAP = PREFIX + "textures/colormap/";

    /**
     * Ensures mock-client.jar exists at the given path; creates it if missing.
     */
    public static Path ensureFixtureJar(Path fixturesDir) throws IOException {
        Path dir = fixturesDir.toAbsolutePath().normalize();
        Files.createDirectories(dir);
        Path jarPath = dir.resolve("mock-client.jar");
        if (Files.exists(jarPath)) {
            return jarPath;
        }
        build(jarPath);
        return jarPath;
    }

    public static void build(Path outPath) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream(512 * 1024);
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            putPng(zos, TEXTURES_BLOCK + "stone.png", solidColorPng(16, 16, 0x7a, 0x7a, 0x7a));
            putPng(zos, TEXTURES_BLOCK + "grass_block_top.png", solidColorPng(16, 16, 0x88, 0xbb, 0x55));
            putPng(zos, TEXTURES_BLOCK + "grass_block_side.png", solidColorPng(16, 16, 0x8a, 0xbc, 0x5a));
            putPng(zos, TEXTURES_BLOCK + "grass_block_side_overlay.png", solidColorPng(16, 16, 0x88, 0xbb, 0x55));
            putPng(zos, TEXTURES_BLOCK + "dirt.png", solidColorPng(16, 16, 0x8b, 0x6b, 0x42));
            putPng(zos, TEXTURES_BLOCK + "oak_leaves.png", solidColorPng(16, 16, 0x35, 0x7a, 0x2d));
            putPng(zos, TEXTURES_BLOCK + "water_still.png", solidColorPng(16, 16, 0x3f, 0x76, 0xe4));
            putPng(zos, TEXTURES_BLOCK + "oak_planks.png", solidColorPng(16, 16, 0xab, 0x8b, 0x56));
            putPng(zos, TEXTURES_BLOCK + "cobblestone.png", solidColorPng(16, 16, 0x80, 0x80, 0x80));
            putPng(zos, TEXTURES_BLOCK + "oak_log_top.png", solidColorPng(16, 16, 0xb5, 0x9d, 0x6d));
            putPng(zos, TEXTURES_BLOCK + "oak_log.png", solidColorPng(16, 16, 0x8f, 0x7a, 0x55));
            putPng(zos, TEXTURES_BLOCK + "iron_bars.png", solidColorPng(16, 16, 0xa0, 0xa0, 0xa0));
            putPng(zos, TEXTURES_BLOCK + "glass.png", solidColorPng(16, 16, 0xc0, 0xe8, 0xff));
            putPng(zos, TEXTURES_BLOCK + "fire_0.png", solidColorPng(16, 16, 0xff, 0x80, 0x00));
            putPng(zos, TEXTURES_BLOCK + "lava_still.png", solidColorPng(16, 16, 0xff, 0x44, 0x00));
            putPng(zos, TEXTURES_BLOCK + "nether_portal.png", solidColorPng(16, 16, 0x60, 0x00, 0xff));
            putPng(zos, TEXTURES_BLOCK + "oak_door_top.png", solidColorPng(16, 16, 0xab, 0x8b, 0x56));
            putPng(zos, TEXTURES_BLOCK + "oak_door_bottom.png", solidColorPng(16, 16, 0xab, 0x8b, 0x56));
            putPng(zos, TEXTURES_BLOCK + "oak_trapdoor.png", solidColorPng(16, 16, 0xab, 0x8b, 0x56));

            putColormap(zos, "grass");
            putColormap(zos, "foliage");
            putColormap(zos, "water");

            putJson(zos, BLOCKSTATES + "stone.json", "{\"variants\":{\"\":{\"model\":\"minecraft:block/stone\"}}}");
            putJson(zos, BLOCKSTATES + "grass_block.json", "{\"variants\":{\"\":[{\"model\":\"minecraft:block/grass_block\"}]}}");
            putJson(zos, BLOCKSTATES + "oak_leaves.json", "{\"variants\":{\"\":{\"model\":\"minecraft:block/oak_leaves\"}}}");
            putJson(zos, BLOCKSTATES + "water.json", "{\"variants\":{\"\":{\"model\":\"minecraft:block/water\"}}}");
            putJson(zos, BLOCKSTATES + "oak_slab.json", "{\"variants\":{\"type=bottom\":{\"model\":\"minecraft:block/oak_slab\"},\"type=top\":{\"model\":\"minecraft:block/oak_slab_top\"},\"type=double\":{\"model\":\"minecraft:block/oak_planks\"}}}");
            putJson(zos, BLOCKSTATES + "oak_stairs.json", "{\"variants\":{\"facing=east,half=bottom,shape=straight\":{\"model\":\"minecraft:block/oak_stairs\"},\"facing=west,half=bottom,shape=straight\":{\"model\":\"minecraft:block/oak_stairs\",\"y\":180}}}");
            putJson(zos, BLOCKSTATES + "oak_fence.json", "{\"multipart\":[{\"apply\":{\"model\":\"minecraft:block/oak_fence_post\"}},{\"when\":{\"north\":\"true\"},\"apply\":{\"model\":\"minecraft:block/oak_fence_side\"}}]}");
            putJson(zos, BLOCKSTATES + "cobblestone_wall.json", "{\"multipart\":[{\"apply\":{\"model\":\"minecraft:block/cobblestone_wall_post\"}}]}");
            putJson(zos, BLOCKSTATES + "oak_door.json", "{\"variants\":{\"half=lower,facing=north,open=false\":{\"model\":\"minecraft:block/oak_door_bottom\"},\"half=upper,facing=north,open=false\":{\"model\":\"minecraft:block/oak_door_top\"}}}");
            putJson(zos, BLOCKSTATES + "oak_trapdoor.json", "{\"variants\":{\"half=bottom,open=false\":{\"model\":\"minecraft:block/oak_trapdoor_bottom\"}}}");
            putJson(zos, BLOCKSTATES + "glass_pane.json", "{\"multipart\":[{\"apply\":{\"model\":\"minecraft:block/glass_pane_noside\"}}]}");
            putJson(zos, BLOCKSTATES + "iron_bars.json", "{\"multipart\":[{\"apply\":{\"model\":\"minecraft:block/iron_bars_post\"}}]}");
            putJson(zos, BLOCKSTATES + "fire.json", "{\"variants\":{\"\":{\"model\":\"minecraft:block/fire\"}}}");
            putJson(zos, BLOCKSTATES + "lava.json", "{\"variants\":{\"\":{\"model\":\"minecraft:block/lava\"}}}");
            putJson(zos, BLOCKSTATES + "nether_portal.json", "{\"variants\":{\"axis=z\":{\"model\":\"minecraft:block/nether_portal_ns\"}}}");

            putJson(zos, MODELS_BLOCK + "stone.json", "{\"parent\":\"minecraft:block/cube_all\",\"textures\":{\"all\":\"minecraft:block/stone\"}}");
            putJson(zos, MODELS_BLOCK + "oak_leaves.json", "{\"parent\":\"minecraft:block/leaves\",\"textures\":{\"all\":\"minecraft:block/oak_leaves\"}}");
            putJson(zos, MODELS_BLOCK + "water.json", "{\"textures\":{\"particle\":\"minecraft:block/water_still\"}}");
            putJson(zos, MODELS_BLOCK + "oak_slab.json", "{\"parent\":\"minecraft:block/slab\",\"textures\":{\"bottom\":\"minecraft:block/oak_planks\",\"top\":\"minecraft:block/oak_planks\",\"side\":\"minecraft:block/oak_planks\"}}");
            putJson(zos, MODELS_BLOCK + "oak_slab_top.json", "{\"parent\":\"minecraft:block/slab_top\",\"textures\":{\"bottom\":\"minecraft:block/oak_planks\",\"top\":\"minecraft:block/oak_planks\",\"side\":\"minecraft:block/oak_planks\"}}");
            putJson(zos, MODELS_BLOCK + "oak_planks.json", "{\"parent\":\"minecraft:block/cube_all\",\"textures\":{\"all\":\"minecraft:block/oak_planks\"}}");
            putJson(zos, MODELS_BLOCK + "oak_stairs.json", "{\"parent\":\"minecraft:block/stairs\",\"textures\":{\"bottom\":\"minecraft:block/oak_planks\",\"top\":\"minecraft:block/oak_planks\",\"side\":\"minecraft:block/oak_planks\"}}");
            putJson(zos, MODELS_BLOCK + "oak_fence_post.json", "{\"parent\":\"minecraft:block/fence_post\",\"textures\":{\"texture\":\"minecraft:block/oak_planks\"}}");
            putJson(zos, MODELS_BLOCK + "oak_fence_side.json", "{\"parent\":\"minecraft:block/fence_side\",\"textures\":{\"texture\":\"minecraft:block/oak_planks\"}}");
            putJson(zos, MODELS_BLOCK + "cobblestone_wall_post.json", "{\"parent\":\"minecraft:block/wall_post\",\"textures\":{\"wall\":\"minecraft:block/cobblestone\"}}");
            putJson(zos, MODELS_BLOCK + "oak_door_bottom.json", "{\"parent\":\"minecraft:block/door_bottom\",\"textures\":{\"bottom\":\"minecraft:block/oak_door_bottom\",\"top\":\"minecraft:block/oak_door_top\"}}");
            putJson(zos, MODELS_BLOCK + "oak_door_top.json", "{\"parent\":\"minecraft:block/door_top\",\"textures\":{\"bottom\":\"minecraft:block/oak_door_bottom\",\"top\":\"minecraft:block/oak_door_top\"}}");
            putJson(zos, MODELS_BLOCK + "oak_trapdoor_bottom.json", "{\"parent\":\"minecraft:block/trapdoor_bottom\",\"textures\":{\"texture\":\"minecraft:block/oak_trapdoor\"}}");
            putJson(zos, MODELS_BLOCK + "fire.json", "{\"elements\":[{\"from\":[0,0,0],\"to\":[16,16,0],\"faces\":{\"north\":{\"uv\":[0,0,16,16],\"texture\":\"#fire\"}}}],\"textures\":{\"fire\":\"minecraft:block/fire_0\"}}");
            putJson(zos, MODELS_BLOCK + "lava.json", "{\"textures\":{\"still\":\"minecraft:block/lava_still\"}}");
            putJson(zos, MODELS_BLOCK + "nether_portal_ns.json", "{\"elements\":[{\"from\":[0,0,0],\"to\":[16,16,0.1],\"faces\":{\"north\":{\"uv\":[0,0,16,16],\"texture\":\"#portal\"}}}],\"textures\":{\"portal\":\"minecraft:block/nether_portal\"}}");
            putJson(zos, MODELS_BLOCK + "cube_all.json", "{\"elements\":[{\"from\":[0,0,0],\"to\":[16,16,16],\"faces\":{\"north\":{\"uv\":[0,0,16,16],\"texture\":\"#all\"},\"south\":{\"uv\":[0,0,16,16],\"texture\":\"#all\"},\"east\":{\"uv\":[0,0,16,16],\"texture\":\"#all\"},\"west\":{\"uv\":[0,0,16,16],\"texture\":\"#all\"},\"up\":{\"uv\":[0,0,16,16],\"texture\":\"#all\"},\"down\":{\"uv\":[0,0,16,16],\"texture\":\"#all\"}}}]}");
            putJson(zos, MODELS_BLOCK + "grass_block.json", "{\"elements\":[{\"from\":[0,0,0],\"to\":[16,16,16],\"faces\":{\"up\":{\"uv\":[0,0,16,16],\"texture\":\"#top\"},\"down\":{\"uv\":[0,0,16,16],\"texture\":\"#bottom\"},\"north\":{\"uv\":[0,0,16,16],\"texture\":\"#side\"},\"south\":{\"uv\":[0,0,16,16],\"texture\":\"#side\"},\"west\":{\"uv\":[0,0,16,16],\"texture\":\"#side\"},\"east\":{\"uv\":[0,0,16,16],\"texture\":\"#side\"}}}]}");
            putJson(zos, MODELS_BLOCK + "leaves.json", "{\"parent\":\"minecraft:block/cube_all\",\"textures\":{\"all\":\"#all\"}}");
            putJson(zos, MODELS_BLOCK + "slab.json", "{\"elements\":[{\"from\":[0,0,0],\"to\":[16,8,16],\"faces\":{\"up\":{\"uv\":[0,0,16,16],\"texture\":\"#top\"},\"down\":{\"uv\":[0,0,16,16],\"texture\":\"#bottom\"},\"north\":{\"uv\":[0,0,16,8],\"texture\":\"#side\"},\"south\":{\"uv\":[0,0,16,8],\"texture\":\"#side\"},\"west\":{\"uv\":[0,0,16,8],\"texture\":\"#side\"},\"east\":{\"uv\":[0,0,16,8],\"texture\":\"#side\"}}}]}");
            putJson(zos, MODELS_BLOCK + "slab_top.json", "{\"elements\":[{\"from\":[0,8,0],\"to\":[16,16,16],\"faces\":{\"up\":{\"uv\":[0,0,16,16],\"texture\":\"#top\"},\"down\":{\"uv\":[0,0,16,16],\"texture\":\"#bottom\"},\"north\":{\"uv\":[0,0,16,8],\"texture\":\"#side\"},\"south\":{\"uv\":[0,0,16,8],\"texture\":\"#side\"},\"west\":{\"uv\":[0,0,16,8],\"texture\":\"#side\"},\"east\":{\"uv\":[0,0,16,8],\"texture\":\"#side\"}}}]}");
            putJson(zos, MODELS_BLOCK + "stairs.json", "{\"elements\":[{\"from\":[0,0,0],\"to\":[16,8,16],\"faces\":{\"up\":{\"uv\":[0,0,16,16],\"texture\":\"#top\"},\"down\":{\"uv\":[0,0,16,16],\"texture\":\"#bottom\"},\"north\":{\"uv\":[0,0,16,8],\"texture\":\"#side\"},\"south\":{\"uv\":[0,0,16,8],\"texture\":\"#side\"},\"west\":{\"uv\":[0,0,16,8],\"texture\":\"#side\"},\"east\":{\"uv\":[0,0,16,8],\"texture\":\"#side\"}}}]}");
            putJson(zos, MODELS_BLOCK + "fence_post.json", "{\"elements\":[{\"from\":[6,0,6],\"to\":[10,16,10],\"faces\":{\"north\":{\"uv\":[0,0,4,16],\"texture\":\"#texture\"},\"south\":{\"uv\":[0,0,4,16],\"texture\":\"#texture\"},\"east\":{\"uv\":[0,0,4,16],\"texture\":\"#texture\"},\"west\":{\"uv\":[0,0,4,16],\"texture\":\"#texture\"},\"up\":{\"uv\":[0,0,4,4],\"texture\":\"#texture\"},\"down\":{\"uv\":[0,0,4,4],\"texture\":\"#texture\"}}}]}");
            putJson(zos, MODELS_BLOCK + "fence_side.json", "{\"elements\":[{\"from\":[7,0,0],\"to\":[9,16,8],\"faces\":{\"north\":{\"uv\":[0,0,2,16],\"texture\":\"#texture\"},\"up\":{\"uv\":[0,0,2,8],\"texture\":\"#texture\"},\"down\":{\"uv\":[0,0,2,8],\"texture\":\"#texture\"}}}]}");
            putJson(zos, MODELS_BLOCK + "wall_post.json", "{\"elements\":[{\"from\":[6,0,6],\"to\":[10,16,10],\"faces\":{\"north\":{\"uv\":[0,0,4,16],\"texture\":\"#wall\"},\"south\":{\"uv\":[0,0,4,16],\"texture\":\"#wall\"},\"east\":{\"uv\":[0,0,4,16],\"texture\":\"#wall\"},\"west\":{\"uv\":[0,0,4,16],\"texture\":\"#wall\"},\"up\":{\"uv\":[0,0,4,4],\"texture\":\"#wall\"},\"down\":{\"uv\":[0,0,4,4],\"texture\":\"#wall\"}}}]}");
            putJson(zos, MODELS_BLOCK + "door_bottom.json", "{\"elements\":[{\"from\":[0,0,0],\"to\":[16,16,3],\"faces\":{\"north\":{\"uv\":[0,0,16,16],\"texture\":\"#bottom\"},\"south\":{\"uv\":[0,0,16,16],\"texture\":\"#bottom\"},\"east\":{\"uv\":[0,0,16,16],\"texture\":\"#bottom\"},\"west\":{\"uv\":[0,0,16,16],\"texture\":\"#bottom\"},\"up\":{\"uv\":[0,0,16,3],\"texture\":\"#bottom\"},\"down\":{\"uv\":[0,0,16,3],\"texture\":\"#bottom\"}}}]}");
            putJson(zos, MODELS_BLOCK + "door_top.json", "{\"elements\":[{\"from\":[0,0,0],\"to\":[16,16,3],\"faces\":{\"north\":{\"uv\":[0,0,16,16],\"texture\":\"#top\"},\"south\":{\"uv\":[0,0,16,16],\"texture\":\"#top\"},\"east\":{\"uv\":[0,0,16,16],\"texture\":\"#top\"},\"west\":{\"uv\":[0,0,16,16],\"texture\":\"#top\"},\"up\":{\"uv\":[0,0,16,3],\"texture\":\"#top\"},\"down\":{\"uv\":[0,0,16,3],\"texture\":\"#top\"}}}]}");
            putJson(zos, MODELS_BLOCK + "trapdoor_bottom.json", "{\"elements\":[{\"from\":[0,0,0],\"to\":[16,3,16],\"faces\":{\"up\":{\"uv\":[0,0,16,16],\"texture\":\"#texture\"},\"down\":{\"uv\":[0,0,16,16],\"texture\":\"#texture\"},\"north\":{\"uv\":[0,0,16,3],\"texture\":\"#texture\"},\"south\":{\"uv\":[0,0,16,3],\"texture\":\"#texture\"},\"west\":{\"uv\":[0,0,16,3],\"texture\":\"#texture\"},\"east\":{\"uv\":[0,0,16,3],\"texture\":\"#texture\"}}}]}");
            putJson(zos, MODELS_BLOCK + "glass_pane_noside.json", "{\"elements\":[{\"from\":[7,0,7],\"to\":[9,16,9],\"faces\":{\"north\":{\"uv\":[0,0,2,16],\"texture\":\"#pane\"},\"south\":{\"uv\":[0,0,2,16],\"texture\":\"#pane\"},\"east\":{\"uv\":[0,0,2,16],\"texture\":\"#pane\"},\"west\":{\"uv\":[0,0,2,16],\"texture\":\"#pane\"},\"up\":{\"uv\":[0,0,2,2],\"texture\":\"#pane\"},\"down\":{\"uv\":[0,0,2,2],\"texture\":\"#pane\"}}}]}");
            putJson(zos, MODELS_BLOCK + "iron_bars_post.json", "{\"elements\":[{\"from\":[7,0,7],\"to\":[9,16,9],\"faces\":{\"north\":{\"uv\":[0,0,2,16],\"texture\":\"#bars\"},\"south\":{\"uv\":[0,0,2,16],\"texture\":\"#bars\"},\"east\":{\"uv\":[0,0,2,16],\"texture\":\"#bars\"},\"west\":{\"uv\":[0,0,2,16],\"texture\":\"#bars\"},\"up\":{\"uv\":[0,0,2,2],\"texture\":\"#bars\"},\"down\":{\"uv\":[0,0,2,2],\"texture\":\"#bars\"}}}]}");
        }
        Files.write(outPath, baos.toByteArray());
    }

    private static byte[] solidColorPng(int w, int h, int r, int g, int b) throws IOException {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        int rgb = 0xff000000 | (r << 16) | (g << 8) | b;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                img.setRGB(x, y, rgb);
            }
        }
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(img, "png", baos);
        return baos.toByteArray();
    }

    /** Grass colormap: gradient from cool (top) to warm (bottom), left to right humidity. */
    private static void putColormap(ZipOutputStream zos, String name) throws IOException {
        int w = 256;
        int h = 256;
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                double temp = 1.0 - (double) y / (h - 1);
                double hum = (double) x / (w - 1);
                int r = (int) (80 + temp * 80 + hum * 40);
                int g = (int) (120 + temp * 60 + hum * 30);
                int b = (int) (40 + temp * 50);
                r = Math.max(0, Math.min(255, r));
                g = Math.max(0, Math.min(255, g));
                b = Math.max(0, Math.min(255, b));
                img.setRGB(x, y, (r << 16) | (g << 8) | b);
            }
        }
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(img, "png", baos);
        putEntry(zos, COLORMAP + name + ".png", baos.toByteArray());
    }

    private static void putPng(ZipOutputStream zos, String path, byte[] png) throws IOException {
        putEntry(zos, path, png);
    }

    private static void putJson(ZipOutputStream zos, String path, String json) throws IOException {
        putEntry(zos, path, json.getBytes(StandardCharsets.UTF_8));
    }

    private static void putEntry(ZipOutputStream zos, String path, byte[] data) throws IOException {
        ZipEntry e = new ZipEntry(path);
        zos.putNextEntry(e);
        zos.write(data);
        zos.closeEntry();
    }
}
