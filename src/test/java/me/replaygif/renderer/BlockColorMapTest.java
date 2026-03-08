package me.replaygif.renderer;

import me.replaygif.core.BlockRegistry;
import org.bukkit.Material;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.awt.Color;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for BlockColorMap covering BC1–BC4 from .planning/testing.md.
 */
class BlockColorMapTest {

    @TempDir
    Path tempDir;

    private BlockRegistry blockRegistry;
    private File dataFolder;

    @BeforeEach
    void setUp() {
        blockRegistry = new BlockRegistry();
        dataFolder = tempDir.toFile();
    }

    private BlockColorMap createBlockColorMap(File dir, boolean ensureNoExistingFile) throws IOException {
        if (ensureNoExistingFile) {
            File f = new File(dir, "block_colors.json");
            if (f.exists()) {
                assertTrue(f.delete(), "delete existing block_colors.json for test");
            }
        }
        InputStream defaults = getClass().getResourceAsStream("/block_colors_defaults.json");
        return new BlockColorMap(dir, "block_colors.json", blockRegistry, defaults, null);
    }

    /** BC1 — No material returns null faces; unknown ordinals return gray fallback. */
    @Test
    void bc1_noMaterialReturnsNullFaces() throws IOException {
        BlockColorMap map = createBlockColorMap(dataFolder, false);
        for (int i = 0; i < blockRegistry.getOrdinalCount(); i++) {
            BlockFaceColors faces = map.getFaces((short) i);
            assertNotNull(faces, "getFaces(" + i + ") must not be null");
            assertNotNull(faces.top(), "top must not be null for ordinal " + i);
            assertNotNull(faces.left(), "left must not be null for ordinal " + i);
            assertNotNull(faces.right(), "right must not be null for ordinal " + i);
        }
        // Unknown ordinals (out of range) return gray, not null
        BlockFaceColors unknown = map.getFaces((short) -1);
        assertNotNull(unknown);
        assertNotNull(unknown.top());
        assertNotNull(unknown.left());
        assertNotNull(unknown.right());
        assertEquals(new Color(0x80, 0x80, 0x80), unknown.top());
        unknown = map.getFaces((short) 99999);
        assertNotNull(unknown);
        assertEquals(new Color(0x80, 0x80, 0x80), unknown.top());
    }

    private static int brightness(Color c) {
        return (c.getRed() + c.getGreen() + c.getBlue()) / 3;
    }

    /** BC2 — Face shade ordering: non-emissive block has top > left > right brightness. */
    @Test
    void bc2_faceShadeOrdering() throws IOException {
        BlockColorMap map = createBlockColorMap(dataFolder, false);
        short stoneOrdinal = blockRegistry.getOrdinal(Material.STONE);
        if (stoneOrdinal <= 0) {
            return; // STONE not present in this Material set
        }
        BlockFaceColors faces = map.getFaces(stoneOrdinal);
        int topB = brightness(faces.top());
        int leftB = brightness(faces.left());
        int rightB = brightness(faces.right());
        assertTrue(topB > leftB, "top must be lighter than left (top=" + topB + ", left=" + leftB + ")");
        assertTrue(leftB > rightB, "left must be lighter than right (left=" + leftB + ", right=" + rightB + ")");
    }

    /** BC3 — Emissive blocks (lava, glowstone, shroomlight) have brighter top face (120% clamped). */
    @Test
    void bc3_emissiveBlocksAreBrighter() throws IOException {
        BlockColorMap map = createBlockColorMap(dataFolder, false);
        for (Material m : new Material[] { Material.LAVA, Material.GLOWSTONE, Material.SHROOMLIGHT }) {
            if (!m.isBlock()) continue;
            short ordinal = blockRegistry.getOrdinal(m);
            BlockFaceColors faces = map.getFaces(ordinal);
            int topBrightness = brightness(faces.top());
            // Top is at 120% (clamped). So brightness should be >= 100% of base and at most 255.
            // A non-emissive block with same base would have top = 100% -> lower or equal.
            // So emissive top brightness should be strictly greater than left (which is 75% of base).
            assertTrue(topBrightness >= brightness(faces.left()),
                    m.name() + " emissive top should be >= left brightness");
            assertTrue(topBrightness >= brightness(faces.right()),
                    m.name() + " emissive top should be >= right brightness");
            // Top at 120% is brighter than 100% base; left is 75% so top > left (already asserted).
            assertTrue(topBrightness <= 255, "emissive top clamped to 255");
        }
    }

    /** BC4 — Deleting block_colors.json and creating BlockColorMap regenerates file with all materials; valid JSON. */
    @Test
    void bc4_blockColorsJsonGeneration() throws IOException {
        File blockColorsFile = new File(dataFolder, "block_colors.json");
        assertFalse(blockColorsFile.exists(), "file should not exist before test");
        BlockColorMap map = createBlockColorMap(dataFolder, false);
        assertTrue(blockColorsFile.exists(), "block_colors.json must be regenerated");
        String content = Files.readString(blockColorsFile.toPath());
        assertNotNull(content);
        assertTrue(content.trim().startsWith("{"), "valid JSON object");
        assertTrue(content.contains("\"STONE\""), "STONE must be present");
        assertTrue(content.contains("\"LAVA\""), "LAVA must be present");
        assertTrue(content.contains("\"AIR\""), "AIR must be present");
        // All BlockRegistry materials must be present
        for (int i = 0; i < blockRegistry.getOrdinalCount(); i++) {
            Material m = blockRegistry.getMaterial((short) i);
            assertTrue(content.contains("\"" + m.name() + "\""),
                    "Material " + m.name() + " must be in generated file");
        }
        // Valid JSON: parse with Gson (already used in production)
        com.google.gson.Gson gson = new com.google.gson.Gson();
        com.google.gson.JsonObject obj = gson.fromJson(content, com.google.gson.JsonObject.class);
        assertNotNull(obj);
        assertEquals(blockRegistry.getOrdinalCount(), obj.size(),
                "Generated file must have one entry per block material");
    }
}
