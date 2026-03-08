package me.replaygif.renderer;

import java.awt.Color;

/**
 * Top, left, and right face colors for isometric block drawing.
 * See rendering-pipeline.md 1e.
 */
public record BlockFaceColors(Color top, Color left, Color right) {}
