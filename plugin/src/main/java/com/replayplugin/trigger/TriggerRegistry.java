package com.replayplugin.trigger;

import com.replayplugin.capture.CaptureBuffer;
import com.replayplugin.capture.RenderJob;
import com.replayplugin.config.TriggerConfig;
import com.replayplugin.job.RenderQueue;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registers and unregisters Paper event listeners for triggers. Enforces per-player and global cooldowns.
 */
public final class TriggerRegistry {

    private final Plugin plugin;
    private final Map<String, List<Registration>> registrationsByEvent = new ConcurrentHashMap<>();
    private final Map<String, Listener> listenerByEvent = new ConcurrentHashMap<>();
    private final Map<TriggerConfig, Map<UUID, Long>> perPlayerCooldownUntil = new ConcurrentHashMap<>();
    private final Map<TriggerConfig, Long> globalCooldownUntil = new ConcurrentHashMap<>();

    public TriggerRegistry(Plugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Register a trigger: listen for the trigger's event, enforce cooldowns, call capture and optionally enqueue.
     */
    public void register(TriggerConfig config, CaptureBuffer captureBuffer, RenderQueue renderQueue) {
        if (config == null || captureBuffer == null) return;
        String eventClassName = config.getEvent();
        if (eventClassName == null || !PlayerExtractionMap.isKnownEvent(eventClassName)) return;

        Class<? extends Event> eventClass = PlayerExtractionMap.getEventClass(eventClassName).orElse(null);
        if (eventClass == null) return;

        Registration reg = new Registration(config, captureBuffer, renderQueue);
        registrationsByEvent.compute(eventClassName, (k, list) -> {
            List<Registration> l = list != null ? new ArrayList<>(list) : new ArrayList<>();
            l.add(reg);
            return l;
        });

        listenerByEvent.computeIfAbsent(eventClassName, k -> {
            Listener listener = new Listener() {};
            Bukkit.getPluginManager().registerEvent(
                    eventClass,
                    listener,
                    EventPriority.NORMAL,
                    (l, event) -> dispatch(eventClassName, event),
                    plugin);
            return listener;
        });
    }

    private void dispatch(String eventClassName, Event event) {
        List<Registration> list = registrationsByEvent.get(eventClassName);
        if (list == null || list.isEmpty()) return;

        var playerOpt = PlayerExtractionMap.getPlayer(event);
        if (playerOpt.isEmpty()) return;
        Player player = playerOpt.get();

        for (Registration reg : list) {
            if (!reg.config.isEnabled()) continue;
            if (!cooldownAllowed(reg, player)) continue;

            RenderJob job = reg.captureBuffer.onTrigger(player, reg.config);
            if (job != null && reg.renderQueue != null) reg.renderQueue.enqueue(job);
            recordCooldown(reg, player);
        }
    }

    private boolean cooldownAllowed(Registration reg, Player player) {
        long now = System.currentTimeMillis();
        int perPlayerSec = reg.config.getCooldownPerPlayer();
        int globalSec = reg.config.getCooldownGlobal();

        if (perPlayerSec > 0) {
            Map<UUID, Long> perPlayer = perPlayerCooldownUntil.get(reg.config);
            if (perPlayer != null) {
                Long until = perPlayer.get(player.getUniqueId());
                if (until != null && now < until) return false;
            }
        }
        if (globalSec > 0) {
            Long until = globalCooldownUntil.get(reg.config);
            if (until != null && now < until) return false;
        }
        return true;
    }

    private void recordCooldown(Registration reg, Player player) {
        long now = System.currentTimeMillis();
        int perPlayerSec = reg.config.getCooldownPerPlayer();
        int globalSec = reg.config.getCooldownGlobal();

        if (perPlayerSec > 0) {
            perPlayerCooldownUntil
                    .computeIfAbsent(reg.config, k -> new ConcurrentHashMap<>())
                    .put(player.getUniqueId(), now + perPlayerSec * 1000L);
        }
        if (globalSec > 0) {
            globalCooldownUntil.put(reg.config, now + globalSec * 1000L);
        }
    }

    /**
     * Unregister all listeners for the given event class name.
     */
    public void unregister(String eventClassName) {
        Listener listener = listenerByEvent.remove(eventClassName);
        if (listener != null) HandlerList.unregisterAll(listener);
        List<Registration> removed = registrationsByEvent.remove(eventClassName);
        if (removed != null) {
            for (Registration reg : removed) {
                perPlayerCooldownUntil.remove(reg.config);
                globalCooldownUntil.remove(reg.config);
            }
        }
    }

    public boolean isRegistered(String eventClassName) {
        return listenerByEvent.containsKey(eventClassName);
    }

    /**
     * Unregister all triggers. Call when sidecar fails to start. Re-register via register() when sidecar is running again.
     */
    public void disableAll() {
        List<String> eventClassNames = new ArrayList<>(listenerByEvent.keySet());
        for (String eventClassName : eventClassNames) {
            unregister(eventClassName);
        }
    }

    private static final class Registration {
        final TriggerConfig config;
        final CaptureBuffer captureBuffer;
        final RenderQueue renderQueue;

        Registration(TriggerConfig config, CaptureBuffer captureBuffer, RenderQueue renderQueue) {
            this.config = config;
            this.captureBuffer = captureBuffer;
            this.renderQueue = renderQueue;
        }
    }
}
