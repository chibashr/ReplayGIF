package me.replaygif.core;

/**
 * Immutable record of one boss bar visible to the player at capture time.
 * Title is plain text; progress 0.0–1.0; color is BossBar.BarColor name.
 */
public final class BossBarRecord {

    /** Boss bar title as plain text. */
    public final String title;

    /** Fill fraction 0.0–1.0. */
    public final float progress;

    /** BossBar.BarColor name: PINK, BLUE, RED, GREEN, YELLOW, PURPLE, WHITE. */
    public final String color;

    public BossBarRecord(String title, float progress, String color) {
        this.title = title;
        this.progress = progress;
        this.color = color != null ? color : "WHITE";
    }
}
