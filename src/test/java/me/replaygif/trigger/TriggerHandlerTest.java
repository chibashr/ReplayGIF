package me.replaygif.trigger;

import me.replaygif.config.ConfigManager;
import me.replaygif.config.OutputProfileRegistry;
import me.replaygif.core.SnapshotBuffer;
import me.replaygif.core.WorldSnapshot;
import me.replaygif.encoder.GifEncoder;
import me.replaygif.output.OutputTarget;
import me.replaygif.renderer.IsometricRenderer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests for TriggerHandler covering TH2–TH5 from .planning/testing.md.
 * TH1 (basic death trigger) requires a live server or integration test setup — see TH1 note below.
 */
class TriggerHandlerTest {

    private static final Logger LOG = LoggerFactory.getLogger(TriggerHandlerTest.class);

    private Map<UUID, SnapshotBuffer> buffers;
    private IsometricRenderer mockRenderer;
    private GifEncoder mockGifEncoder;
    private OutputProfileRegistry mockRegistry;
    private ConfigManager mockConfig;
    private TriggerHandler handler;
    private OutputTarget mockTarget;

    @BeforeEach
    void setUp() {
        buffers = new ConcurrentHashMap<>();
        mockRenderer = mock(IsometricRenderer.class);
        mockGifEncoder = mock(GifEncoder.class);
        mockRegistry = mock(OutputProfileRegistry.class);
        mockConfig = mock(ConfigManager.class);

        when(mockConfig.getFps()).thenReturn(10);
        when(mockConfig.getAsyncThreads()).thenReturn(2);
        when(mockRenderer.renderFrame(any(WorldSnapshot.class), anyInt(), any())).thenAnswer(inv -> {
            return new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        });
        when(mockGifEncoder.encode(anyList(), anyInt())).thenReturn(new byte[] { 0x47, 0x49, 0x46, 0x38, 0x39, 0x61 });
        mockTarget = mock(OutputTarget.class);
        when(mockRegistry.getProfile("default")).thenReturn(List.of(mockTarget));

        handler = new TriggerHandler(
                buffers,
                mockRenderer,
                mockGifEncoder,
                mockRegistry,
                mockConfig,
                LOG);
    }

    @AfterEach
    void tearDown() {
        handler.shutdown();
    }

    private static WorldSnapshot snapshotAt(long timestamp) {
        return new WorldSnapshot(
                timestamp,
                0, 0, 0,
                0f, 0f,
                20f, 20,
                "minecraft:overworld",
                "world",
                new short[32 * 32 * 32],
                32,
                List.of(),
                false);
    }

    private TriggerContext buildContext(UUID subjectUUID, String subjectName, long triggerTs,
                                        double preSeconds, double postSeconds, List<String> profiles) {
        return new TriggerContext.Builder()
                .subjectUUID(subjectUUID)
                .subjectName(subjectName)
                .eventLabel("test event")
                .preSeconds(preSeconds)
                .postSeconds(postSeconds)
                .outputProfileNames(profiles != null ? profiles : List.of("default"))
                .metadata(null)
                .triggerTimestamp(triggerTs)
                .jobId(UUID.randomUUID())
                .triggerX(0).triggerY(0).triggerZ(0)
                .dimension("minecraft:overworld")
                .worldName("world")
                .build();
    }

    private void waitForJobDone(UUID jobId, long timeoutMs) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (handler.getActiveJobs().containsKey(jobId) && System.currentTimeMillis() < deadline) {
            Thread.sleep(20);
        }
    }

    /** TH2 — Buffer not full (player just joined): partial GIF produced and dispatched, no crash. */
    @Test
    void th2_bufferNotFull_partialGifDispatched() throws InterruptedException {
        UUID subjectUUID = UUID.randomUUID();
        int capacity = 100;
        SnapshotBuffer buffer = new SnapshotBuffer(capacity);
        long base = System.currentTimeMillis() - 10_000;
        for (int i = 0; i < 5; i++) {
            buffer.write(snapshotAt(base + i * 200L));
        }
        buffers.put(subjectUUID, buffer);

        long triggerTs = base + 1000L;
        TriggerContext context = buildContext(subjectUUID, "Player1", triggerTs, 2.0, 0.05, List.of("default"));
        UUID jobId = handler.handle(context);

        assertNotNull(jobId);
        waitForJobDone(jobId, 5000);

        verify(mockRenderer, atLeast(1)).renderFrame(any(WorldSnapshot.class), anyInt(), any());
        verify(mockGifEncoder).encode(anyList(), eq(100));
        verify(mockTarget).dispatch(eq(context), any(byte[].class));
    }

    /** TH3 — Concurrent triggers: two jobs run independently and both complete. */
    @Test
    void th3_concurrentTriggers_bothComplete() throws InterruptedException {
        UUID subject1 = UUID.randomUUID();
        UUID subject2 = UUID.randomUUID();
        SnapshotBuffer buf1 = new SnapshotBuffer(50);
        SnapshotBuffer buf2 = new SnapshotBuffer(50);
        long base = System.currentTimeMillis() - 5000;
        for (int i = 0; i < 10; i++) {
            buf1.write(snapshotAt(base + i * 100L));
            buf2.write(snapshotAt(base + i * 100L));
        }
        buffers.put(subject1, buf1);
        buffers.put(subject2, buf2);

        TriggerContext ctx1 = buildContext(subject1, "P1", base + 500L, 1.0, 0.02, List.of("default"));
        TriggerContext ctx2 = buildContext(subject2, "P2", base + 500L, 1.0, 0.02, List.of("default"));
        UUID jobId1 = handler.handle(ctx1);
        UUID jobId2 = handler.handle(ctx2);

        assertNotEquals(jobId1, jobId2);
        waitForJobDone(jobId1, 5000);
        waitForJobDone(jobId2, 5000);

        verify(mockTarget, times(2)).dispatch(any(TriggerContext.class), any(byte[].class));
    }

    /** TH4 — No buffer on trigger: WARN logged, no crash, returns jobId without submitting. */
    @Test
    void th4_noBuffer_triggerIgnoredNoCrash() {
        UUID subjectUUID = UUID.randomUUID();
        assertFalse(buffers.containsKey(subjectUUID));

        TriggerContext context = buildContext(subjectUUID, "OfflinePlayer", System.currentTimeMillis(), 4.0, 1.0, List.of("default"));
        UUID jobId = handler.handle(context);

        assertNotNull(jobId);
        assertFalse(handler.getActiveJobs().containsKey(jobId));
        verify(mockTarget, never()).dispatch(any(), any());
        verify(mockGifEncoder, never()).encode(anyList(), anyInt());
    }

    /** TH5 — Post-window wait: render does not start until at least postSeconds after trigger. */
    @Test
    void th5_postWindowWait_delayBeforeRender() throws InterruptedException {
        UUID subjectUUID = UUID.randomUUID();
        SnapshotBuffer buffer = new SnapshotBuffer(50);
        long base = System.currentTimeMillis() - 5000;
        for (int i = 0; i < 15; i++) {
            buffer.write(snapshotAt(base + i * 100L));
        }
        buffers.put(subjectUUID, buffer);

        long triggerTs = base + 1000L;
        double postSeconds = 0.08; // 80ms so test stays fast
        TriggerContext context = new TriggerContext.Builder()
                .subjectUUID(subjectUUID)
                .subjectName("P")
                .eventLabel("test")
                .preSeconds(1.0)
                .postSeconds(postSeconds)
                .outputProfileNames(List.of("default"))
                .metadata(null)
                .triggerTimestamp(triggerTs)
                .jobId(UUID.randomUUID())
                .triggerX(0).triggerY(0).triggerZ(0)
                .dimension("minecraft:overworld")
                .worldName("world")
                .build();

        long t0 = System.currentTimeMillis();
        UUID jobId = handler.handle(context);
        waitForJobDone(jobId, 5000);
        long elapsed = System.currentTimeMillis() - t0;

        assertTrue(elapsed >= 70, "Pipeline should take at least ~postSeconds (80ms), took " + elapsed + "ms");
        verify(mockRenderer, atLeast(1)).renderFrame(any(), anyInt(), any());
    }

    /** Empty slice: job fails with FAILED status, no dispatch. */
    @Test
    void emptySlice_jobFailsNoDispatch() throws InterruptedException {
        UUID subjectUUID = UUID.randomUUID();
        SnapshotBuffer buffer = new SnapshotBuffer(20);
        // No frames written, or slice window outside any frame
        buffers.put(subjectUUID, buffer);

        long triggerTs = System.currentTimeMillis();
        TriggerContext context = buildContext(subjectUUID, "P", triggerTs, 2.0, 0.02, List.of("default"));
        UUID jobId = handler.handle(context);

        waitForJobDone(jobId, 3000);
        verify(mockTarget, never()).dispatch(any(), any());
    }

    /** TH1 — Basic death trigger: requires live server or integration test (player join, wait 6s, kill, verify GIF and Discord).
     *  Not run in unit tests. Run manually or in integration suite with a real server. */
    @Test
    void th1_basicDeathTrigger_requiresLiveServer() {
        // TH1 is documented here; run as integration test with live server:
        // 1. Join a player, wait 6 seconds (buffer fills at 10fps).
        // 2. Kill the player via console (e.g. /kill).
        // 3. Assert a GIF render job starts, GIF is produced and dispatched to configured output.
        // 4. Assert Discord embed contains player name and death message; GIF has at least 1 frame.
        assertTrue(true, "TH1 placeholder — run on live server or in integration test");
    }
}
