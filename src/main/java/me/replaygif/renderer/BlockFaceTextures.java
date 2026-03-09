package me.replaygif.renderer;

import java.awt.image.BufferedImage;

/**
 * Three face images for one block in isometric projection. BlockTextureRegistry produces these
 * from bundled or client JAR block textures; top is the horizontal face, side is used for both
 * left and right vertical faces (renderer may apply shading).
 */
public record BlockFaceTextures(BufferedImage top, BufferedImage side) {}
