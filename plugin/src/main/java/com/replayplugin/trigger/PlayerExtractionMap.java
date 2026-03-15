package com.replayplugin.trigger;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

import java.util.Optional;
import java.util.function.Function;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Maps event class simple names to extractors that yield the associated Player.
 * Used to validate trigger config and to get the player from a fired event.
 */
public final class PlayerExtractionMap {

    private static final Map<String, Function<Event, Player>> EXTRACTORS = new ConcurrentHashMap<>();
    private static final Map<String, Class<? extends Event>> EVENT_CLASSES = new ConcurrentHashMap<>();

    static {
        put(PlayerDeathEvent.class.getSimpleName(), PlayerDeathEvent.class, e -> ((PlayerDeathEvent) e).getEntity());
        put(BlockBreakEvent.class.getSimpleName(), BlockBreakEvent.class, e -> ((BlockBreakEvent) e).getPlayer());
        put(BlockPlaceEvent.class.getSimpleName(), BlockPlaceEvent.class, e -> ((BlockPlaceEvent) e).getPlayer());
        put(PlayerMoveEvent.class.getSimpleName(), PlayerMoveEvent.class, e -> ((PlayerMoveEvent) e).getPlayer());
        put(PlayerInteractEvent.class.getSimpleName(), PlayerInteractEvent.class, e -> ((PlayerInteractEvent) e).getPlayer());
        put(EntityDamageByEntityEvent.class.getSimpleName(), EntityDamageByEntityEvent.class, e -> {
            EntityDamageByEntityEvent ev = (EntityDamageByEntityEvent) e;
            if (ev.getDamager() instanceof Player) return (Player) ev.getDamager();
            return null;
        });
        put(PlayerRespawnEvent.class.getSimpleName(), PlayerRespawnEvent.class, e -> ((PlayerRespawnEvent) e).getPlayer());
        put(PlayerJoinEvent.class.getSimpleName(), PlayerJoinEvent.class, e -> ((PlayerJoinEvent) e).getPlayer());
        put(PlayerQuitEvent.class.getSimpleName(), PlayerQuitEvent.class, e -> ((PlayerQuitEvent) e).getPlayer());
    }

    private static void put(String name, Class<? extends Event> eventClass, Function<Event, Player> extractor) {
        EXTRACTORS.put(name, extractor);
        EVENT_CLASSES.put(name, eventClass);
    }

    private PlayerExtractionMap() {}

    public static Optional<Player> getPlayer(Event event) {
        if (event == null) return Optional.empty();
        Function<Event, Player> fn = EXTRACTORS.get(event.getClass().getSimpleName());
        if (fn == null) return Optional.empty();
        Player p = fn.apply(event);
        return Optional.ofNullable(p);
    }

    public static boolean isKnownEvent(String eventClassName) {
        return eventClassName != null && EVENT_CLASSES.containsKey(eventClassName);
    }

    public static Optional<Class<? extends Event>> getEventClass(String eventClassName) {
        return Optional.ofNullable(EVENT_CLASSES.get(eventClassName));
    }
}
