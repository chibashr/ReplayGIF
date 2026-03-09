package me.replaygif.renderer;

import me.replaygif.core.EntitySnapshot;
import me.replaygif.core.WorldSnapshot;
import org.bukkit.entity.EntityType;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Point;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Pre-render analysis and drawing for hurt and death particles (vanilla-style).
 * Follows the ExplosionSynthesizer pattern: Stage 0.5 analyzes consecutive frames
 * for hurtProgress and isDead transitions; Stage 1g draws particles after entity sprites.
 */
public class HurtParticleSynthesizer {

    private static final Color HURT_COLOR = new Color(0xFF0000);
    private static final int HURT_DOT_COUNT_MIN = 4;
    private static final int HURT_DOT_COUNT_MAX = 6;
    private static final int HURT_DOT_RADIUS_PX = 2;
    private static final int DEATH_DOT_COUNT = 12;
    private static final int DEATH_DOT_RADIUS_PX = 2;
    private static final double DEATH_ANGLE_STEP_DEG = 30;
    private static final Color DEATH_COLOR_DEFAULT = new Color(0x8B7355);
    private static final Color DEATH_COLOR_SLIME = new Color(0x00AA00);
    private static final Color DEATH_COLOR_MAGMA_CUBE = new Color(0xFF6600);
    private static final Color DEATH_COLOR_SNOW_GOLEM = new Color(0xAAAAFF);

    private final int tileWidth;
    private final int tileHeight;

    /** Hurt records per frame index (frame where hurtProgress first > 0). */
    private Map<Integer, List<HurtRecord>> hurtRecordsByFrame = new HashMap<>();
    /** Death records per frame index (disappearFrame = frame where burst is drawn). */
    private Map<Integer, List<DeathRecord>> deathRecordsByFrame = new HashMap<>();

    public HurtParticleSynthesizer(int tileWidth, int tileHeight) {
        this.tileWidth = tileWidth;
        this.tileHeight = tileHeight;
    }

    /**
     * Stage 0.5: analyze all frames for hurt and death transitions. Call once per render job before drawing.
     */
    public void analyze(List<WorldSnapshot> frames) {
        hurtRecordsByFrame = new HashMap<>();
        deathRecordsByFrame = new HashMap<>();
        if (frames == null || frames.isEmpty()) {
            return;
        }
        for (int f = 0; f < frames.size(); f++) {
            WorldSnapshot snap = frames.get(f);
            WorldSnapshot prev = f > 0 ? frames.get(f - 1) : null;
            for (EntitySnapshot e : snap.entities) {
                float prevHurt = prev != null ? findHurtProgress(prev, e.uuid) : 0f;
                if (prevHurt <= 0f && e.hurtProgress > 0f) {
                    hurtRecordsByFrame
                            .computeIfAbsent(f, k -> new ArrayList<>())
                            .add(new HurtRecord(e.relX, e.relY, e.relZ, f, e.type, e.uuid, e.boundingWidth, e.boundingHeight));
                }
            }
            if (prev != null) {
                for (EntitySnapshot e : prev.entities) {
                    boolean deadOrAbsent = false;
                    EntitySnapshot inCurr = findEntity(snap, e.uuid);
                    if (inCurr == null) {
                        deadOrAbsent = true;
                    } else if (inCurr.isDead) {
                        deadOrAbsent = true;
                    }
                    if (deadOrAbsent) {
                        deathRecordsByFrame
                                .computeIfAbsent(f, k -> new ArrayList<>())
                                .add(new DeathRecord(e.relX, e.relY, e.relZ, f, e.type, e.uuid));
                    }
                }
            }
        }
    }

    private static float findHurtProgress(WorldSnapshot snap, UUID uuid) {
        EntitySnapshot e = findEntity(snap, uuid);
        return e != null ? e.hurtProgress : 0f;
    }

    private static EntitySnapshot findEntity(WorldSnapshot snap, UUID uuid) {
        for (EntitySnapshot e : snap.entities) {
            if (e.uuid.equals(uuid)) return e;
        }
        return null;
    }

    /**
     * Stage 1g: draw hurt and death particles for this frame. Call after entity pass, before overlays.
     *
     * @param project function to project (relX, relY, relZ) to screen Point
     */
    public void draw(Graphics2D g, int frameIndex, Projector project) {
        List<HurtRecord> hurts = hurtRecordsByFrame.get(frameIndex);
        if (hurts != null) {
            for (HurtRecord r : hurts) {
                drawHurtParticles(g, r, project);
            }
        }
        List<DeathRecord> deaths = deathRecordsByFrame.get(frameIndex);
        if (deaths != null) {
            for (DeathRecord r : deaths) {
                drawDeathBurst(g, r, project);
            }
        }
    }

    private void drawHurtParticles(Graphics2D g, HurtRecord r, Projector project) {
        Point center = project.project(r.relX, r.relY, r.relZ);
        int spriteW = (int) Math.round(r.boundingWidth * tileWidth);
        int spriteH = (int) Math.round(r.boundingHeight * tileHeight * 2);
        if (spriteW < 1) spriteW = 1;
        if (spriteH < 1) spriteH = 1;
        int left = center.x - spriteW / 2;
        int top = (int) (center.y + tileHeight - spriteH);
        long seed = (long) r.frameIndex * r.entityUuid.hashCode();
        int count = HURT_DOT_COUNT_MIN + (int) (Math.abs(seed % (HURT_DOT_COUNT_MAX - HURT_DOT_COUNT_MIN + 1)));
        g.setColor(HURT_COLOR);
        for (int i = 0; i < count; i++) {
            int px = left + (int) (nextSeeded(seed + i * 31) * spriteW);
            int py = top + (int) (nextSeeded(seed + i * 31 + 1) * spriteH);
            fillCircle(g, px, py, HURT_DOT_RADIUS_PX);
        }
    }

    private void drawDeathBurst(Graphics2D g, DeathRecord r, Projector project) {
        Point center = project.project(r.relX, r.relY, r.relZ);
        double radius = (tileWidth / 3.0) * 1.5;
        int angleOffset = Math.floorMod(r.entityUuid.hashCode(), 30);
        Color color = deathColorForType(r.entityType);
        g.setColor(color);
        for (int i = 0; i < DEATH_DOT_COUNT; i++) {
            double angleDeg = i * DEATH_ANGLE_STEP_DEG + angleOffset;
            double angleRad = Math.toRadians(angleDeg);
            int dx = (int) Math.round(Math.cos(angleRad) * radius);
            int dy = (int) Math.round(Math.sin(angleRad) * radius);
            fillCircle(g, center.x + dx, center.y + dy, DEATH_DOT_RADIUS_PX);
        }
    }

    private static Color deathColorForType(EntityType type) {
        return switch (type.name()) {
            case "SLIME" -> DEATH_COLOR_SLIME;
            case "MAGMA_CUBE" -> DEATH_COLOR_MAGMA_CUBE;
            case "SNOW_GOLEM" -> DEATH_COLOR_SNOW_GOLEM;
            default -> DEATH_COLOR_DEFAULT;
        };
    }

    private static double nextSeeded(long seed) {
        long x = seed * 6364136223846793005L + 1442695040888963407L;
        x ^= x >>> 32;
        return (x & 0x7FFF_FFFF) / (double) 0x8000_0000;
    }

    private static void fillCircle(Graphics2D g, int cx, int cy, int r) {
        g.fillOval(cx - r, cy - r, r * 2, r * 2);
    }

    public Map<Integer, List<HurtRecord>> getHurtRecordsByFrame() {
        return hurtRecordsByFrame;
    }

    public Map<Integer, List<DeathRecord>> getDeathRecordsByFrame() {
        return deathRecordsByFrame;
    }

    public record HurtRecord(double relX, double relY, double relZ, int frameIndex,
                             EntityType entityType, UUID entityUuid,
                             double boundingWidth, double boundingHeight) {}

    public record DeathRecord(double relX, double relY, double relZ, int disappearFrame,
                              EntityType entityType, UUID entityUuid) {}

    @FunctionalInterface
    public interface Projector {
        Point project(double relX, double relY, double relZ);
    }
}
