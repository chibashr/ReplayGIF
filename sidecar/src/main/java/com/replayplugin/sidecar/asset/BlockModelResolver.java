package com.replayplugin.sidecar.asset;

import com.fasterxml.jackson.databind.JsonNode;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Resolves block ID and state properties to BlockGeometry by loading blockstate and model JSON and resolving textures.
 */
public final class BlockModelResolver {

    private static final Set<String> BIOME_TINT_BLOCKS = Set.of(
            "minecraft:grass_block", "minecraft:grass", "minecraft:tall_grass",
            "minecraft:oak_leaves", "minecraft:birch_leaves", "minecraft:jungle_leaves",
            "minecraft:spruce_leaves", "minecraft:acacia_leaves", "minecraft:dark_oak_leaves",
            "minecraft:mangrove_leaves", "minecraft:cherry_leaves", "minecraft:azalea_leaves",
            "minecraft:flowering_azalea_leaves", "minecraft:water", "minecraft:water_cauldron"
    );

    private final AssetManager assetManager;

    public BlockModelResolver(AssetManager assetManager) {
        this.assetManager = assetManager;
    }

    /**
     * Resolves block to geometry. Unsupported or unresolvable blocks return a single full-cube tinted with the block's top texture average color.
     */
    public BlockGeometry resolve(String blockId, Map<String, String> blockStateProperties) {
        String normalizedId = blockId.contains(":") ? blockId : "minecraft:" + blockId;
        Map<String, String> props = blockStateProperties != null ? blockStateProperties : Map.of();
        boolean needsBiomeTint = BIOME_TINT_BLOCKS.contains(normalizedId);

        try {
            JsonNode blockstate = assetManager.getBlockstateJson(normalizedId);
            List<String> modelRefs = selectModels(blockstate, props);
            if (modelRefs.isEmpty()) {
                return fallbackFullCube(normalizedId, needsBiomeTint);
            }
            List<BlockGeometry.Element> allElements = new ArrayList<>();
            Map<String, BufferedImage> allTextures = new HashMap<>();
            for (String modelRef : modelRefs) {
                BlockGeometry geometry = resolveModelChain(modelRef, new HashMap<>(), new ArrayList<>());
                if (geometry != null && !geometry.getElements().isEmpty()) {
                    allElements.addAll(geometry.getElements());
                    allTextures.putAll(geometry.getTextures());
                }
            }
            if (allElements.isEmpty()) {
                return fallbackFullCube(normalizedId, needsBiomeTint);
            }
            return new BlockGeometry(allElements, allTextures, needsBiomeTint);
        } catch (AssetNotFoundException e) {
            return fallbackFullCube(normalizedId, needsBiomeTint);
        } catch (Exception e) {
            return fallbackFullCube(normalizedId, needsBiomeTint);
        }
    }

    private List<String> selectModels(JsonNode blockstate, Map<String, String> props) {
        List<String> out = new ArrayList<>();
        JsonNode variants = blockstate.get("variants");
        JsonNode multipart = blockstate.get("multipart");
        if (variants != null) {
            String key = buildVariantKey(props);
            JsonNode variant = variants.get(key);
            if (variant == null) variant = variants.get("");
            if (variant != null) {
                if (variant.isArray()) {
                    for (JsonNode v : variant) out.add(modelRefFromVariant(v));
                } else {
                    out.add(modelRefFromVariant(variant));
                }
            }
        }
        if (multipart != null && multipart.isArray()) {
            for (JsonNode part : multipart) {
                JsonNode when = part.get("when");
                JsonNode apply = part.get("apply");
                if (when == null || when.isEmpty() || whenMatches(when, props)) {
                    if (apply != null) {
                        if (apply.isArray()) {
                            for (JsonNode a : apply) out.add(modelRefFromVariant(a));
                        } else {
                            out.add(modelRefFromVariant(apply));
                        }
                    }
                }
            }
        }
        return out;
    }

    private String buildVariantKey(Map<String, String> props) {
        if (props.isEmpty()) return "";
        return props.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(e -> e.getKey() + "=" + e.getValue())
                .collect(Collectors.joining(","));
    }

    private boolean whenMatches(JsonNode when, Map<String, String> props) {
        JsonNode or = when.get("OR");
        if (or != null && or.isArray()) {
            for (JsonNode cond : or) {
                if (whenConditionMatches(cond, props)) return true;
            }
            return false;
        }
        return whenConditionMatches(when, props);
    }

    private boolean whenConditionMatches(JsonNode cond, Map<String, String> props) {
        for (var it = cond.fields(); it.hasNext(); ) {
            var e = it.next();
            String key = e.getKey();
            String expected = e.getValue().asText();
            String actual = props.get(key);
            if (actual == null || !expected.equals(actual)) return false;
        }
        return true;
    }

    private String modelRefFromVariant(JsonNode variant) {
        JsonNode model = variant.get("model");
        return model != null ? model.asText() : "";
    }

    private BlockGeometry resolveModelChain(String modelPath, Map<String, String> inheritedTextures, List<String> visited) {
        if (modelPath == null || modelPath.isEmpty() || visited.contains(modelPath)) {
            return null;
        }
        try {
            JsonNode modelJson = assetManager.getModelJson(modelPath);
            if (modelJson == null) return null;
            visited.add(modelPath);
            Map<String, String> textures = new HashMap<>(inheritedTextures);
            JsonNode texNode = modelJson.get("textures");
            if (texNode != null) {
                for (var it = texNode.fields(); it.hasNext(); ) {
                    var e = it.next();
                    textures.put(e.getKey(), e.getValue().asText());
                }
            }
            JsonNode parentNode = modelJson.get("parent");
            List<BlockGeometry.Element> elements = new ArrayList<>();
            Map<String, BufferedImage> resolvedTextures = new HashMap<>();
            if (parentNode != null) {
                String parent = parentNode.asText();
                BlockGeometry parentGeo = resolveModelChain(parent, new HashMap<>(), new ArrayList<>(visited));
                if (parentGeo != null) {
                    elements.addAll(parentGeo.getElements());
                    resolvedTextures.putAll(parentGeo.getTextures());
                }
            }
            JsonNode elementsNode = modelJson.get("elements");
            if (elementsNode != null && elementsNode.isArray()) {
                for (JsonNode el : elementsNode) {
                    BlockGeometry.Element element = parseElement(el, textures);
                    if (element != null) elements.add(element);
                }
            }
            for (String texKey : textures.keySet()) {
                if (resolvedTextures.containsKey(texKey)) continue;
                String path = resolveTexturePath(textures.get(texKey), textures);
                if (path != null) {
                    try {
                        BufferedImage img = assetManager.getTexture(path);
                        if (img != null) resolvedTextures.put(texKey, img);
                    } catch (AssetNotFoundException ignored) {
                    }
                }
            }
            return new BlockGeometry(elements, resolvedTextures, false);
        } catch (AssetNotFoundException e) {
            return null;
        }
    }

    private BlockGeometry.Element parseElement(JsonNode el, Map<String, String> textures) {
        JsonNode from = el.get("from");
        JsonNode to = el.get("to");
        JsonNode faces = el.get("faces");
        if (from == null || to == null || faces == null) return null;
        double[] fromArr = new double[]{from.get(0).asDouble(), from.get(1).asDouble(), from.get(2).asDouble()};
        double[] toArr = new double[]{to.get(0).asDouble(), to.get(1).asDouble(), to.get(2).asDouble()};
        Map<String, BlockGeometry.FaceInfo> faceMap = new HashMap<>();
        for (var it = faces.fields(); it.hasNext(); ) {
            var e = it.next();
            String dir = e.getKey();
            JsonNode face = e.getValue();
            String texRef = face.has("texture") ? face.get("texture").asText() : null;
            if (texRef == null) continue;
            if (texRef.startsWith("#")) texRef = texRef.substring(1);
            double[] uv = null;
            if (face.has("uv") && face.get("uv").isArray()) {
                JsonNode uvNode = face.get("uv");
                uv = new double[]{
                        uvNode.get(0).asDouble(), uvNode.get(1).asDouble(),
                        uvNode.get(2).asDouble(), uvNode.get(3).asDouble()};
            }
            faceMap.put(dir, new BlockGeometry.FaceInfo(texRef, uv));
        }
        return new BlockGeometry.Element(fromArr, toArr, faceMap);
    }

    private String resolveTexturePath(String value, Map<String, String> textures) {
        if (value == null) return null;
        if (value.startsWith("#")) {
            return resolveTexturePath(textures.get(value.substring(1)), textures);
        }
        if (value.contains(":")) {
            return value; // e.g. minecraft:block/stone -> we pass to getTexture as block/stone or just stone
        }
        return "block/" + value;
    }

    private BlockGeometry fallbackFullCube(String blockId, boolean requiresBiomeTint) {
        Color avg = getAverageTopColor(blockId);
        BufferedImage tint = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        tint.setRGB(0, 0, avg.getRGB());
        Map<String, BufferedImage> textures = Map.of("all", tint);
        double[] zero = new double[]{0, 0, 0};
        double[] full = new double[]{16, 16, 16};
        double[] uv = new double[]{0, 0, 16, 16};
        Map<String, BlockGeometry.FaceInfo> faces = Map.of(
                "north", new BlockGeometry.FaceInfo("all", uv),
                "south", new BlockGeometry.FaceInfo("all", uv),
                "east", new BlockGeometry.FaceInfo("all", uv),
                "west", new BlockGeometry.FaceInfo("all", uv),
                "up", new BlockGeometry.FaceInfo("all", uv),
                "down", new BlockGeometry.FaceInfo("all", uv));
        BlockGeometry.Element cube = new BlockGeometry.Element(zero, full, faces);
        return new BlockGeometry(List.of(cube), textures, requiresBiomeTint);
    }

    private Color getAverageTopColor(String blockId) {
        try {
            JsonNode blockstate = assetManager.getBlockstateJson(blockId);
            JsonNode variants = blockstate != null ? blockstate.get("variants") : null;
            String firstModel = null;
            if (variants != null) {
                JsonNode first = variants.get("");
                if (first == null && variants.size() > 0) {
                    first = variants.elements().next();
                }
                if (first != null) {
                    if (first.isArray()) first = first.size() > 0 ? first.get(0) : null;
                    if (first != null) firstModel = first.has("model") ? first.get("model").asText() : null;
                }
            }
            if (firstModel == null) return new Color(128, 128, 128);
            JsonNode modelJson = assetManager.getModelJson(firstModel);
            if (modelJson == null) return new Color(128, 128, 128);
            Map<String, String> tex = new HashMap<>();
            if (modelJson.has("textures")) {
                modelJson.get("textures").fields().forEachRemaining(e -> tex.put(e.getKey(), e.getValue().asText()));
            }
            String topTex = null;
            JsonNode elements = modelJson.get("elements");
            if (elements != null) {
                for (JsonNode el : elements) {
                    if (el.has("faces") && el.get("faces").has("up")) {
                        topTex = el.get("faces").get("up").get("texture").asText();
                        break;
                    }
                }
            }
            if (topTex == null) topTex = tex.isEmpty() ? null : tex.values().iterator().next();
            if (topTex != null) {
                if (topTex.startsWith("#")) topTex = tex.get(topTex.substring(1));
                if (topTex != null) {
                    String path = topTex.contains(":") ? topTex : "block/" + topTex;
                    BufferedImage img = assetManager.getTexture(path);
                    if (img != null) return averageColor(img);
                }
            }
        } catch (Exception ignored) {
        }
        return new Color(128, 128, 128);
    }

    private static Color averageColor(BufferedImage img) {
        long r = 0, g = 0, b = 0;
        int n = 0;
        int w = img.getWidth();
        int h = img.getHeight();
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int rgb = img.getRGB(x, y);
                int a = (rgb >> 24) & 0xff;
                if (a < 128) continue;
                r += (rgb >> 16) & 0xff;
                g += (rgb >> 8) & 0xff;
                b += rgb & 0xff;
                n++;
            }
        }
        if (n == 0) return new Color(128, 128, 128);
        return new Color((int) (r / n), (int) (g / n), (int) (b / n));
    }
}
