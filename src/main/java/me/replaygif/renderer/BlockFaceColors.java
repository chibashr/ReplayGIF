package me.replaygif.renderer;

import java.awt.Color;

/**
 * Three face colors for one block in isometric projection. BlockColorMap produces these
 * with shading (darker left/right) and optional emissive boost on top.
 */
public record BlockFaceColors(Color top, Color left, Color right) {}
