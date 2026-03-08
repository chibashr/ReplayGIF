package me.replaygif.renderer;

/**
 * Width and height in blocks for one entity; used to scale the entity sprite in the
 * isometric view so larger entities (e.g. giants) appear bigger. EntitySpriteRegistry
 * provides these from entity_bounds.json or default 0.6×1.8.
 */
public record BoundingBox(double width, double height) {}
