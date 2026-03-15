package com.replayplugin;

import com.replayplugin.capture.*;
import com.replayplugin.job.JobSerializer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class JobSerializationTest {

    @Test
    @DisplayName("SER-001: Serialize render job to JSON - valid JSON, all required fields")
    void ser001_serializeToJson() throws Exception {
        RenderJob job = minimalJob();
        String json = JobSerializer.serialize(job);
        assertNotNull(json);
        assertTrue(json.contains("player_uuid"));
        assertTrue(json.contains("event_type"));
        assertTrue(json.contains("timestamp"));
        assertTrue(json.contains("pre_frames"));
        assertTrue(json.contains("post_frames"));
        assertTrue(json.contains("render_config"));
    }

    @Test
    @DisplayName("SER-002: Deserialize same JSON back - round-trip equal")
    void ser002_roundTrip() throws Exception {
        RenderJob job = minimalJob();
        String json = JobSerializer.serialize(job);
        RenderJob back = JobSerializer.deserialize(json);
        assertEquals(job.getPlayerUuid(), back.getPlayerUuid());
        assertEquals(job.getEventType(), back.getEventType());
        assertEquals(job.getTimestamp(), back.getTimestamp());
        assertEquals(job.getPreFrames().size(), back.getPreFrames().size());
        assertEquals(job.getRenderConfig().getFps(), back.getRenderConfig().getFps());
    }

    @Test
    @DisplayName("SER-003: render_config reflects per-trigger overrides")
    void ser003_renderConfigPerTrigger() {
        List<DestinationDto> dests = List.of(new DestinationDto("disk", null), new DestinationDto("in_game", null));
        RenderConfigDto config = new RenderConfigDto(15, 24, 4, "follow_player", true, dests);
        RenderJob job = new RenderJob(UUID.randomUUID(), "p", "BlockBreakEvent", "20260314-153042", config, List.of(), List.of());
        String json = null;
        try {
            json = JobSerializer.serialize(job);
        } catch (Exception e) { fail(e); }
        assertTrue(json.contains("15"));
        assertTrue(json.contains("24"));
        assertTrue(json.contains("4"));
    }

    @Test
    @DisplayName("SER-004: GIF filename format <player>_<event>_<timestamp>.gif")
    void ser004_gifFilenameFormat() {
        RenderJob job = new RenderJob(UUID.randomUUID(), "steve", "PlayerDeathEvent", "20260314-153042",
                minimalJob().getRenderConfig(), List.of(), List.of());
        String name = job.getGifFilename();
        assertTrue(name.matches("steve_PlayerDeathEvent_\\d{8}-\\d{6}\\.gif"));
    }

    private static RenderJob minimalJob() {
        EntityState state = new EntityState(0, 64, 0, 0, 0, "STANDING", Map.of("main_hand", "minecraft:air", "off_hand", "minecraft:air", "head", "minecraft:air", "chest", "minecraft:air", "legs", "minecraft:air", "feet", "minecraft:air"), "");
        FrameSnapshot frame = new FrameSnapshot(0, 0, state, List.of());
        RenderConfigDto config = new RenderConfigDto(10, 32, 5, "follow_player", true, List.of(new DestinationDto("disk", null)));
        return new RenderJob(UUID.randomUUID(), "p", "PlayerDeathEvent", "20260314-153042", config, List.of(frame), List.of());
    }
}
