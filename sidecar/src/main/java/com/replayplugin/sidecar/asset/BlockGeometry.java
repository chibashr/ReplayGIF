package com.replayplugin.sidecar.asset;

import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Map;

/**
 * Resolved block geometry: elements with per-face texture and UV, plus resolved texture images.
 * Used by the isometric renderer.
 */
public final class BlockGeometry {

    private final List<Element> elements;
    private final Map<String, BufferedImage> textures;
    private final boolean requiresBiomeTint;

    public BlockGeometry(List<Element> elements, Map<String, BufferedImage> textures, boolean requiresBiomeTint) {
        this.elements = List.copyOf(elements);
        this.textures = Map.copyOf(textures);
        this.requiresBiomeTint = requiresBiomeTint;
    }

    public List<Element> getElements() {
        return elements;
    }

    public Map<String, BufferedImage> getTextures() {
        return textures;
    }

    public boolean isRequiresBiomeTint() {
        return requiresBiomeTint;
    }

    /**
     * One axis-aligned box with per-face texture key and UV (in 0–16 model space).
     */
    public static final class Element {
        private final double[] from;
        private final double[] to;
        private final Map<String, FaceInfo> faces;

        public Element(double[] from, double[] to, Map<String, FaceInfo> faces) {
            this.from = from.clone();
            this.to = to.clone();
            this.faces = Map.copyOf(faces);
        }

        public double[] getFrom() {
            return from.clone();
        }

        public double[] getTo() {
            return to.clone();
        }

        public Map<String, FaceInfo> getFaces() {
            return faces;
        }
    }

    /**
     * Face texture reference and UV rectangle [u0, v0, u1, v1] in model space (0–16).
     */
    public static final class FaceInfo {
        private final String texture;
        private final double[] uv;

        public FaceInfo(String texture, double[] uv) {
            this.texture = texture;
            this.uv = uv == null ? null : uv.clone();
        }

        public String getTexture() {
            return texture;
        }

        public double[] getUv() {
            return uv == null ? null : uv.clone();
        }
    }
}
