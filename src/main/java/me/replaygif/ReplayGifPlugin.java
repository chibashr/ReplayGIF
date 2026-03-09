package me.replaygif;

import me.replaygif.api.ReplayGifAPI;
import me.replaygif.api.ReplayGifAPIImpl;
import me.replaygif.api.ApiTriggerListener;
import me.replaygif.config.ConfigManager;
import me.replaygif.config.OutputProfileRegistry;
import me.replaygif.config.TriggerRuleRegistry;
import me.replaygif.core.ActionBarTracker;
import me.replaygif.core.BlockBreakTracker;
import me.replaygif.core.CombatEventTracker;
import me.replaygif.core.BlockRegistry;
import me.replaygif.core.SnapshotBuffer;
import me.replaygif.core.SnapshotScheduler;
import me.replaygif.encoder.GifEncoder;
import me.replaygif.renderer.BlockColorMap;
import me.replaygif.renderer.BlockTextureRegistry;
import me.replaygif.renderer.EntitySpriteRegistry;
import me.replaygif.renderer.HurtParticleSynthesizer;
import me.replaygif.renderer.IsometricRenderer;
import me.replaygif.renderer.SkinCache;
import me.replaygif.trigger.DeathListener;
import me.replaygif.trigger.DynamicListenerRegistry;
import me.replaygif.trigger.TriggerHandler;
import me.replaygif.trigger.WebhookInboundServer;
import me.replaygif.compat.EntityCustomNameResolver;
import me.replaygif.compat.EntityCustomNameResolverLegacy;
import me.replaygif.compat.EntityCustomNameResolverModern;
import me.replaygif.compat.MessageSender;
import me.replaygif.compat.MessageSenderLegacy;
import me.replaygif.compat.MessageSenderModern;
import me.replaygif.compat.VersionHelper;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

import javax.imageio.ImageIO;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.awt.image.BufferedImage;

public final class ReplayGifPlugin extends JavaPlugin implements Listener {

    private ConfigManager configManager;
    private BlockRegistry blockRegistry;
    private BlockColorMap blockColorMap;
    private BlockTextureRegistry blockTextureRegistry;
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
    private EntityCustomNameResolver entityCustomNameResolver;
    private MessageSender messageSender;
    private BlockBreakTracker blockBreakTracker;
    private CombatEventTracker combatEventTracker;
    private ActionBarTracker actionBarTracker;

    @Override
    public void onEnable() {
        getSLF4JLogger().info("ReplayGif enabling...");

        VersionHelper.init(getServer());
        entityCustomNameResolver = VersionHelper.useModernNameableApi()
                ? new EntityCustomNameResolverModern()
                : new EntityCustomNameResolverLegacy();
        messageSender = VersionHelper.useModernSendMessage()
                ? new MessageSenderModern()
                : new MessageSenderLegacy();

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

        // 3b. BlockTextureRegistry — bundled 1.21 block textures (optional)
        blockTextureRegistry = null;
        if (configManager.getBlockTexturesEnabled()) {
            try {
                blockTextureRegistry = new BlockTextureRegistry(this, blockRegistry);
            } catch (IOException e) {
                getSLF4JLogger().warn("Block textures not available (run extractBlockTextures to bundle them): {}", e.getMessage());
            }
        }

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

        // 6. HurtParticleSynthesizer + crack stages + IsometricRenderer
        HurtParticleSynthesizer hurtParticleSynthesizer = new HurtParticleSynthesizer(
                configManager.getTileWidth(),
                configManager.getTileHeight());
        BufferedImage[] crackStages = loadCrackStages();
        IsometricRenderer isometricRenderer = new IsometricRenderer(
                configManager.getVolumeSize(),
                configManager.getTileWidth(),
                configManager.getTileHeight(),
                configManager.getCutOffset(),
                blockColorMap,
                blockRegistry,
                blockTextureRegistry,
                entitySpriteRegistry,
                skinCache,
                hurtParticleSynthesizer,
                crackStages);
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

        // 22.5. BlockBreakTracker — per-player block break state for snapshot capture
        blockBreakTracker = new BlockBreakTracker();
        getServer().getPluginManager().registerEvents(blockBreakTracker, this);
        getSLF4JLogger().info("BlockBreakTracker registered.");

        // 22.6. CombatEventTracker — attack records per tick for snapshot capture
        combatEventTracker = new CombatEventTracker();
        getServer().getPluginManager().registerEvents(combatEventTracker, this);
        getSLF4JLogger().info("CombatEventTracker registered.");

        // 22.7. ActionBarTracker — per-player action bar text for snapshot capture (best-effort)
        actionBarTracker = new ActionBarTracker(getSLF4JLogger());
        getServer().getPluginManager().registerEvents(actionBarTracker, this);

        // ReplayGifTriggerEvent listener (MONITOR, respects allow_api_triggers)
        getServer().getPluginManager().registerEvents(
                new ApiTriggerListener(triggerHandler, configManager, getSLF4JLogger()), this);

        // Commands: reload, status, test (with tab completion)
        var replaygifCommand = getCommand("replaygif");
        if (replaygifCommand != null) {
            ReplayGifCommand cmd = new ReplayGifCommand(this, messageSender);
            replaygifCommand.setExecutor(cmd);
            replaygifCommand.setTabCompleter(cmd);
        }

        // 17. SnapshotScheduler — start last
        snapshotScheduler = new SnapshotScheduler(this, buffers, configManager, blockRegistry, entityCustomNameResolver, blockBreakTracker, actionBarTracker, combatEventTracker);
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

    /** Loads crack_stage_0.png through crack_stage_9.png from plugin resources. Returns null if any fail. */
    private BufferedImage[] loadCrackStages() {
        BufferedImage[] stages = new BufferedImage[10];
        for (int i = 0; i < 10; i++) {
            String path = "crack_stage_" + i + ".png";
            try (InputStream in = getResource(path)) {
                if (in != null) {
                    stages[i] = ImageIO.read(in);
                }
            } catch (IOException ignored) {
                // fall through
            }
            if (stages[i] == null) {
                return null;
            }
        }
        return stages;
    }

    /**
     * Reload config, clear all buffers (then re-create for online players),
     * restart SnapshotScheduler, and restart HttpServer only if port or enabled changed.
     * Edge case: if a player dies on the same tick as reload, the death event runs after
     * this method returns; buffers have already been repopulated by UUID, so the trigger
     * finds their buffer (possibly empty). TriggerHandler.handle() also tolerates no buffer
     * (logs WARN and returns) so no exception reaches the console.
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
        snapshotScheduler = new SnapshotScheduler(this, buffers, configManager, blockRegistry, entityCustomNameResolver, blockBreakTracker, actionBarTracker, combatEventTracker);
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
