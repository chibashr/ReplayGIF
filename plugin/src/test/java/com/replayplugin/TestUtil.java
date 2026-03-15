package com.replayplugin;

import com.replayplugin.capture.RenderJob;
import com.replayplugin.job.QueueStatus;
import com.replayplugin.job.RenderQueue;
import org.bukkit.damage.DamageSource;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.mockito.Mockito.mock;

/**
 * Test-only utilities for plugin tests.
 */
public final class TestUtil {

    private TestUtil() {}

    /** Creates a minimal PlayerDeathEvent for Paper 1.21 (requires DamageSource). Deprecated constructor used for test simplicity. */
    @SuppressWarnings("deprecation")
    public static PlayerDeathEvent createPlayerDeathEvent(Player player, String deathMessage) {
        DamageSource damageSource = mock(DamageSource.class);
        return new PlayerDeathEvent(player, damageSource, Collections.emptyList(), 0, deathMessage);
    }

    /** RenderQueue implementation that counts enqueue calls and always accepts. */
    public static RenderQueue countingRenderQueue(AtomicInteger enqueueCount) {
        return new RenderQueue() {
            @Override
            public boolean enqueue(RenderJob job) {
                enqueueCount.incrementAndGet();
                return true;
            }
            @Override
            public QueueStatus getStatus() {
                return new QueueStatus(null, 0);
            }
            @Override
            public void clear() {}
        };
    }
}
