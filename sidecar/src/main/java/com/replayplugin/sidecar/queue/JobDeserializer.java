package com.replayplugin.sidecar.queue;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.replayplugin.capture.RenderJob;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.logging.Logger;

/**
 * Deserializes a queue JSON file to RenderJob per IPC-CONTRACT.md. On parse error, logs and returns empty.
 */
public final class JobDeserializer {

    private static final Logger LOG = Logger.getLogger(JobDeserializer.class.getName());
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * Deserialize job from JSON file. Returns empty and logs on JsonParseException; does not throw.
     */
    public static Optional<RenderJob> deserialize(Path jsonFile) {
        try {
            String json = Files.readString(jsonFile);
            return Optional.of(MAPPER.readValue(json, RenderJob.class));
        } catch (JsonParseException e) {
            LOG.severe("Malformed JSON in " + jsonFile.getFileName() + ": " + e.getMessage());
            return Optional.empty();
        } catch (Exception e) {
            LOG.severe("Failed to read/parse " + jsonFile.getFileName() + ": " + e.getMessage());
            return Optional.empty();
        }
    }
}
