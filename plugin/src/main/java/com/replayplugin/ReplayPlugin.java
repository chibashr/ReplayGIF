package com.replayplugin;

import com.replayplugin.capture.CaptureBuffer;
import com.replayplugin.capture.CaptureBufferImpl;
import com.replayplugin.config.PluginConfig;
import com.replayplugin.config.TriggerConfig;
import com.replayplugin.job.RenderQueue;
import com.replayplugin.job.RenderQueueImpl;
import com.replayplugin.sidecar.DefaultSidecarProcessLauncher;
import com.replayplugin.sidecar.SidecarManager;
import com.replayplugin.sidecar.SidecarProcessLauncher;
import com.replayplugin.trigger.TriggerRegistry;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;

/**
 * Paper plugin: capture rolling world snapshots on trigger events, enqueue render jobs for the sidecar.
 */
public final class ReplayPlugin extends JavaPlugin {

    private PluginConfig config;
    private TriggerRegistry triggerRegistry;
    private CaptureBuffer captureBuffer;
    private RenderQueue renderQueue;
    private SidecarManager sidecarManager;
    private SidecarProcessLauncher sidecarProcessLauncher;

    /** Test hook: set before MockBukkit.load(ReplayPlugin.class) to avoid starting a real process. */
    static volatile SidecarProcessLauncher testSidecarProcessLauncher;

    public static void setTestSidecarProcessLauncher(SidecarProcessLauncher launcher) {
        testSidecarProcessLauncher = launcher;
    }

    /**
     * Set before onEnable() to inject a custom launcher (e.g. for tests). If null, default launcher is used.
     */
    public void setSidecarProcessLauncher(SidecarProcessLauncher launcher) {
        this.sidecarProcessLauncher = launcher;
    }

    @Override
    public void onEnable() {
        config = PluginConfig.load(this);
        triggerRegistry = new TriggerRegistry(this);
        renderQueue = new RenderQueueImpl(this, config);
        captureBuffer = new CaptureBufferImpl(this, config, renderQueue);
        ((CaptureBufferImpl) captureBuffer).startCaptureTask();
        SidecarProcessLauncher launcher = testSidecarProcessLauncher != null ? testSidecarProcessLauncher
                : (sidecarProcessLauncher != null ? sidecarProcessLauncher : new DefaultSidecarProcessLauncher());
        sidecarManager = new SidecarManager(this, config, launcher);
        sidecarManager.onCrash(this::reRegisterTriggers);
        sidecarManager.start();
        if (renderQueue instanceof RenderQueueImpl) {
            ((RenderQueueImpl) renderQueue).setOnEnqueueWithInGameDelivery(
                    job -> sidecarManager.registerPendingInGameDelivery(job.getGifFilename(), job.getPlayerUuid()));
        }
        registerTriggers();
        getCommand("replay").setExecutor(new com.replayplugin.command.ReplayCommand(this));
    }

    @Override
    public void onDisable() {
        if (sidecarManager != null) {
            sidecarManager.shutdown();
        }
        if (captureBuffer != null && captureBuffer instanceof CaptureBufferImpl) {
            ((CaptureBufferImpl) captureBuffer).stopCaptureTask();
        }
    }

    public TriggerRegistry getTriggerRegistry() {
        return triggerRegistry;
    }

    public void reloadConfigFrom(PluginConfig newConfig) {
        this.config = newConfig != null ? newConfig : this.config;
    }

    void reRegisterTriggers() {
        for (Map.Entry<String, TriggerConfig> e : config.getTriggers().entrySet()) {
            triggerRegistry.register(e.getValue(), captureBuffer, renderQueue);
        }
    }

    private void registerTriggers() {
        for (Map.Entry<String, TriggerConfig> e : config.getTriggers().entrySet()) {
            triggerRegistry.register(e.getValue(), captureBuffer, renderQueue);
        }
    }

    public PluginConfig getPluginConfig() { return config; }
    public CaptureBuffer getCaptureBuffer() { return captureBuffer; }
    public RenderQueue getRenderQueue() { return renderQueue; }
    public SidecarManager getSidecarManager() { return sidecarManager; }
}
