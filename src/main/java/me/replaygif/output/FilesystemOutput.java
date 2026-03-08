package me.replaygif.output;

import me.replaygif.trigger.TriggerContext;
import org.slf4j.Logger;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Filesystem output target. Writes GIF to a path within the plugin directory.
 * Path template supports variables; resolved path must stay inside plugin directory.
 */
public class FilesystemOutput implements OutputTarget {

    private final String pathTemplate;
    private final File pluginDataFolder;
    private final Logger logger;

    public FilesystemOutput(String pathTemplate, File pluginDataFolder, Logger logger) {
        this.pathTemplate = pathTemplate;
        this.pluginDataFolder = pluginDataFolder;
        this.logger = logger;
    }

    @Override
    public void dispatch(TriggerContext context, byte[] gifBytes) {
        if (pathTemplate == null || pathTemplate.isEmpty()) {
            logger.error("[{}] FilesystemOutput has empty path_template", context.jobId);
            return;
        }
        String resolved = TemplateVariableResolver.resolveForPath(context, pathTemplate);
        Path base = pluginDataFolder.getAbsoluteFile().toPath().normalize();
        Path resolvedPath = base.resolve(resolved).normalize();
        if (!resolvedPath.startsWith(base)) {
            logger.error("[{}] FilesystemOutput path resolves outside plugin directory: {}", context.jobId, resolved);
            return;
        }
        if (gifBytes == null) {
            gifBytes = new byte[0];
        }
        try {
            Path parent = resolvedPath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.write(resolvedPath, gifBytes);
        } catch (IOException e) {
            logger.error("[{}] FilesystemOutput failed to write file: {}", context.jobId, e.getMessage());
        }
    }
}
