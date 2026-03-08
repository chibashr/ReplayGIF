package me.replaygif;

import me.replaygif.api.ReplayGifAPI;
import me.replaygif.api.ReplayGifAPIImpl;
import me.replaygif.api.ApiTriggerListener;
import me.replaygif.config.ConfigManager;
import me.replaygif.config.OutputProfileRegistry;
import me.replaygif.config.TriggerRuleRegistry;
import me.replaygif.core.BlockRegistry;
import me.replaygif.core.SnapshotBuffer;
import me.replaygif.core.SnapshotScheduler;
import me.replaygif.encoder.GifEncoder;
import me.replaygif.renderer.BlockColorMap;
import me.replaygif.renderer.EntitySpriteRegistry;
import me.replaygif.renderer.IsometricRenderer;
import me.replaygif.renderer.SkinCache;
import me.replaygif.trigger.DeathListener;
import me.replaygif.trigger.DynamicListenerRegistry;
import me.replaygif.trigger.TriggerHandler;
import me.replaygif.trigger.WebhookInboundServer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ReplayGifPlugin extends JavaPlugin implements Listener {

    private ConfigManager configManager;
    private BlockRegistry blockRegistry;
    private BlockColorMap blockColorMap;
    private EntitySpriteRegistry entitySpriteRegistry;
    private SkinCache skinCache;
    private Map<UUID, SnapshotBuffer> buffers;
    private SnapshotScheduler snapshotScheduler;
    private OutputProfileRegistry outputProfileRegistry;
    private TriggerRuleRegistry triggerRuleRegistry;
    private TriggerHandler triggerHandler;
    private WebhookInboundServer webhookInboundServer;
    private ReplayGifAPIImpl replayGifAPIImpl;

    @Override
    public void onEnable() {
        getSLF4JLogger().info("ReplayGif enabling...");

        // 1. ConfigManager — load and validate all config files
        configManager = new ConfigManager(this);
        configManager.saveDefaultConfigs();
        configManager.load();

        // 2. BlockRegistry
        blockRegistry = new BlockRegistry();

        // 3. BlockColorMap — load or generate block_colors.json
        try {
            blockColorMap = new BlockColorMap(
                    getDataFolder(),
                    configManager.getBlockColorsPath(),
                    blockRegistry,
                    getResource("block_colors_defaults.json"));
        } catch (IOException e) {
            getSLF4JLogger().error("Failed to load or generate block_colors.json", e);
            throw new RuntimeException("BlockColorMap failed", e);
        }

        // 4. EntitySpriteRegistry — client jar or bundled sprites + entity_bounds
        try {
            entitySpriteRegistry = new EntitySpriteRegistry(this, configManager.getClientJarPath());
        } catch (IOException e) {
            getSLF4JLogger().error("Failed to load EntitySpriteRegistry", e);
            throw new RuntimeException("EntitySpriteRegistry failed", e);
        }

        // 5. SkinCache — player face cache with TTL
        skinCache = new SkinCache(this, configManager.getSkinRenderingEnabled(), configManager.getSkinCacheTtlSeconds());

        // 10. SnapshotBuffer map (buffers created on join, removed on quit)
        buffers = new ConcurrentHashMap<>();

        // 6–9. Render pipeline and config
        outputProfileRegistry = new OutputProfileRegistry(configManager, this);
        triggerRuleRegistry = new TriggerRuleRegistry(configManager, outputProfileRegistry, getSLF4JLogger());
        IsometricRenderer isometricRenderer = new IsometricRenderer(
                configManager.getVolumeSize(),
                configManager.getTileWidth(),
                configManager.getTileHeight(),
                configManager.getCutOffset(),
                blockColorMap,
                blockRegistry,
                entitySpriteRegistry,
                skinCache);
        GifEncoder gifEncoder = new GifEncoder();
        triggerHandler = new TriggerHandler(
                buffers, isometricRenderer, gifEncoder,
                outputProfileRegistry, configManager, getSLF4JLogger());

        // 12. DeathListener
        getServer().getPluginManager().registerEvents(
                new DeathListener(triggerHandler, triggerRuleRegistry, getSLF4JLogger()), this);

        // 13. DynamicListenerRegistry
        new DynamicListenerRegistry(triggerHandler, triggerRuleRegistry, this, getSLF4JLogger()).register();

        // 14. WebhookInboundServer (binds only if webhook_server.enabled)
        webhookInboundServer = new WebhookInboundServer(configManager, triggerRuleRegistry, triggerHandler, getSLF4JLogger());
        webhookInboundServer.start();

        // ReplayGifTriggerEvent listener (MONITOR, respects allow_api_triggers)
        getServer().getPluginManager().registerEvents(
                new ApiTriggerListener(triggerHandler, configManager, getSLF4JLogger()), this);

        // 15. ReplayGifAPIImpl — register with ServicesManager
        replayGifAPIImpl = new ReplayGifAPIImpl(triggerHandler, configManager, getSLF4JLogger());
        getServer().getServicesManager().register(ReplayGifAPI.class, replayGifAPIImpl, this, ServicePriority.Normal);

        // 16. Player join/quit — create buffer on join, remove on quit
        getServer().getPluginManager().registerEvents(this, this);

        // Commands: reload, status, test
        var replaygifCommand = getCommand("replaygif");
        if (replaygifCommand != null) {
            replaygifCommand.setExecutor(new ReplayGifCommand(this));
        }

        // 17. SnapshotScheduler — start last
        snapshotScheduler = new SnapshotScheduler(this, buffers, configManager, blockRegistry);
        snapshotScheduler.start();
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        int capacity = configManager.getBufferSeconds() * configManager.getFps();
        buffers.put(player.getUniqueId(), new SnapshotBuffer(capacity));
        skinCache.onPlayerJoin(player);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        buffers.remove(event.getPlayer().getUniqueId());
    }

    @Override
    public void onDisable() {
        getSLF4JLogger().info("ReplayGif disabling...");

        // 1. SnapshotScheduler — cancel first
        if (snapshotScheduler != null) {
            snapshotScheduler.cancel();
        }
        // 2. WebhookInboundServer — stop
        if (webhookInboundServer != null) {
            webhookInboundServer.stop();
        }
        // ReplayGifAPIImpl — unregister from ServicesManager
        if (replayGifAPIImpl != null) {
            getServer().getServicesManager().unregister(ReplayGifAPI.class, replayGifAPIImpl);
        }
        // TriggerHandler — shutdown render pool
        if (triggerHandler != null) {
            triggerHandler.shutdown();
        }
        // 6. SnapshotBuffer map — clear
        if (buffers != null) {
            buffers.clear();
        }
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public Map<UUID, SnapshotBuffer> getSnapshotBuffers() {
        return buffers;
    }

    public BlockRegistry getBlockRegistry() {
        return blockRegistry;
    }

    public BlockColorMap getBlockColorMap() {
        return blockColorMap;
    }

    public EntitySpriteRegistry getEntitySpriteRegistry() {
        return entitySpriteRegistry;
    }

    public SkinCache getSkinCache() {
        return skinCache;
    }
}
