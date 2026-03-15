package com.replayplugin.job;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.replayplugin.capture.RenderJob;

/**
 * Serializes RenderJob to JSON per IPC-CONTRACT.md.
 */
public final class JobSerializer {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    /**
     * Serialize job to JSON string exactly per IPC-CONTRACT.md.
     */
    public static String serialize(RenderJob job) throws JsonProcessingException {
        return MAPPER.writeValueAsString(job);
    }

    /**
     * Deserialize job from JSON string.
     */
    public static RenderJob deserialize(String json) throws JsonProcessingException {
        return MAPPER.readValue(json, RenderJob.class);
    }
}
