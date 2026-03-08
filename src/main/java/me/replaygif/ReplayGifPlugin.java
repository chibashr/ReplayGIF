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
    private DynamicListenerRegistry dynamicListenerRegistry;

    @Override
    public void onEnable() {
        getSLF4JLogger().info("ReplayGif enabling...");

        // 1. ConfigManager — load and validate all config files
        configManager = new ConfigManager(this);
        configManager.saveDefaultConfigs();
        configManager.load();
        // (ConfigManager.load() logs "Config loaded and validated.")

        // 2. BlockRegistry
        blockRegistry = new BlockRegistry();
        getSLF4JLogger().info("BlockRegistry ready.");

        // 3. BlockColorMap — load or generate block_colors.json
        try {
            blockColorMap = new BlockColorMap(
                    getDataFolder(),
                    configManager.getBlockColorsPath(),
                    blockRegistry,
                    getResource("block_colors_defaults.json"),
                    getSLF4JLogger());
        } catch (IOException e) {
            getSLF4JLogger().error("Failed to load or generate block_colors.json", e);
            throw new RuntimeException("BlockColorMap failed", e);
        }
        // (BlockColorMap logs "BlockColorMap loaded." or "Generated block_colors.json...")

        // 4. EntitySpriteRegistry — client jar or bundled sprites + entity_bounds
        try {
            entitySpriteRegistry = new EntitySpriteRegistry(this, configManager.getClientJarPath());
        } catch (IOException e) {
            getSLF4JLogger().error("Failed to load EntitySpriteRegistry", e);
            throw new RuntimeException("EntitySpriteRegistry failed", e);
        }
        // (EntitySpriteRegistry logs which mode is active)

        // 5. SkinCache — player face cache with TTL
        skinCache = new SkinCache(this, configManager.getSkinRenderingEnabled(), configManager.getSkinCacheTtlSeconds());
        getSLF4JLogger().info("SkinCache initialized.");

        // 6. IsometricRenderer
        IsometricRenderer isometricRenderer = new IsometricRenderer(
                configManager.getVolumeSize(),
                configManager.getTileWidth(),
                configManager.getTileHeight(),
                configManager.getCutOffset(),
                blockColorMap,
                blockRegistry,
                entitySpriteRegistry,
                skinCache);
        getSLF4JLogger().info("IsometricRenderer ready.");

        // 7. GifEncoder
        GifEncoder gifEncoder = new GifEncoder();
        getSLF4JLogger().info("GifEncoder ready.");

        // 8. OutputProfileRegistry
        outputProfileRegistry = new OutputProfileRegistry(configManager, this);
        getSLF4JLogger().info("OutputProfileRegistry ready.");

        // 9. TriggerRuleRegistry
        triggerRuleRegistry = new TriggerRuleRegistry(configManager, outputProfileRegistry, getSLF4JLogger());
        getSLF4JLogger().info("TriggerRuleRegistry ready.");

        // 10. SnapshotBuffer map — buffers created on join, removed on quit
        buffers = new ConcurrentHashMap<>();
        getSLF4JLogger().info("SnapshotBuffer map initialized.");

        // 11. TriggerHandler
        triggerHandler = new TriggerHandler(
                buffers, isometricRenderer, gifEncoder,
                outputProfileRegistry, configManager, getSLF4JLogger());
        getSLF4JLogger().info("TriggerHandler ready.");

        // 12. DeathListener
        getServer().getPluginManager().registerEvents(
                new DeathListener(triggerHandler, triggerRuleRegistry, getSLF4JLogger()), this);
        getSLF4JLogger().info("DeathListener registered.");

        // 13. DynamicListenerRegistry
        dynamicListenerRegistry = new DynamicListenerRegistry(triggerHandler, triggerRuleRegistry, this, getSLF4JLogger());
        dynamicListenerRegistry.register();
        // (DynamicListenerRegistry logs "DynamicListenerRegistry: registration complete.")

        // 14. WebhookInboundServer (binds only if webhook_server.enabled)
        webhookInboundServer = new WebhookInboundServer(configManager, triggerRuleRegistry, triggerHandler, getSLF4JLogger());
        webhookInboundServer.start();
        // (WebhookInboundServer logs listening port or disabled)

        // 15. ReplayGifAPIImpl — register with ServicesManager
        replayGifAPIImpl = new ReplayGifAPIImpl(triggerHandler, configManager, getSLF4JLogger());
        getServer().getServicesManager().register(ReplayGifAPI.class, replayGifAPIImpl, this, ServicePriority.Normal);
        getSLF4JLogger().info("ReplayGifAPI registered.");

        // 16. Player join/quit — create buffer on join, remove on quit
        getServer().getPluginManager().registerEvents(this, this);
        getSLF4JLogger().info("Player join/quit listener registered.");

        // ReplayGifTriggerEvent listener (MONITOR, respects allow_api_triggers)
        getServer().getPluginManager().registerEvents(
                new ApiTriggerListener(triggerHandler, configManager, getSLF4JLogger()), this);

        // Commands: reload, status, test (with tab completion)
        var replaygifCommand = getCommand("replaygif");
        if (replaygifCommand != null) {
            ReplayGifCommand cmd = new ReplayGifCommand(this);
            replaygifCommand.setExecutor(cmd);
            replaygifCommand.setTabCompleter(cmd);
        }

        // 17. SnapshotScheduler — start last
        snapshotScheduler = new SnapshotScheduler(this, buffers, configManager, blockRegistry);
        snapshotScheduler.start();
        getSLF4JLogger().info("SnapshotScheduler started.");
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
            getSLF4JLogger().info("SnapshotScheduler cancelled.");
        }
        // 2. WebhookInboundServer — stop
        if (webhookInboundServer != null) {
            webhookInboundServer.stop();
            getSLF4JLogger().info("WebhookInboundServer stopped.");
        }
        // 3. TriggerHandler — shutdown render pool
        if (triggerHandler != null) {
            triggerHandler.shutdown();
        }
        // 4. ReplayGifAPIImpl — unregister from ServicesManager
        if (replayGifAPIImpl != null) {
            getServer().getServicesManager().unregister(ReplayGifAPI.class, replayGifAPIImpl);
            getSLF4JLogger().info("ReplayGifAPI unregistered.");
        }
        // 5. SkinCache — shut down skin fetch executor
        if (skinCache != null) {
            skinCache.shutdown();
            getSLF4JLogger().info("SkinCache shut down.");
        }
        // 6. SnapshotBuffer map — clear
        if (buffers != null) {
            buffers.clear();
            getSLF4JLogger().info("SnapshotBuffer map cleared.");
        }
        // 7. DynamicListenerRegistry — explicit cleanup (Paper auto-unregisters)
        if (dynamicListenerRegistry != null) {
            dynamicListenerRegistry.unregister();
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

    public TriggerHandler getTriggerHandler() {
        return triggerHandler;
    }

    public SnapshotScheduler getSnapshotScheduler() {
        return snapshotScheduler;
    }

    public WebhookInboundServer getWebhookInboundServer() {
        return webhookInboundServer;
    }

    public ReplayGifAPIImpl getReplayGifAPIImpl() {
        return replayGifAPIImpl;
    }

    /**
     * Reload config, clear all buffers (then re-create for online players),
     * restart SnapshotScheduler, and restart HttpServer only if port or enabled changed.
     */
    public void reload() {
        configManager.load();

        buffers.clear();
        int capacity = configManager.getBufferSeconds() * configManager.getFps();
        for (Player player : getServer().getOnlinePlayers()) {
            buffers.put(player.getUniqueId(), new SnapshotBuffer(capacity));
        }

        if (snapshotScheduler != null) {
            snapshotScheduler.cancel();
        }
        snapshotScheduler = new SnapshotScheduler(this, buffers, configManager, blockRegistry);
        snapshotScheduler.start();
        getSLF4JLogger().info("SnapshotScheduler restarted.");

        if (webhookInboundServer != null) {
            int currentPort = webhookInboundServer.getPort();
            boolean currentlyEnabled = currentPort >= 0;
            boolean newEnabled = configManager.getWebhookServerEnabled();
            int newPort = configManager.getWebhookServerPort();
            if (currentlyEnabled != newEnabled || (newEnabled && currentPort != newPort)) {
                webhookInboundServer.stop();
                webhookInboundServer.start();
            }
        }
    }
}
