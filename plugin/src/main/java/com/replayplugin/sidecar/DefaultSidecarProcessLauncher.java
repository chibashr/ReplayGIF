package com.replayplugin.sidecar;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Default launcher that starts the sidecar JVM via ProcessBuilder.
 */
public final class DefaultSidecarProcessLauncher implements SidecarProcessLauncher {

    private static final String SIDECAR_JAR_NAME = "sidecar.jar";

    @Override
    public Process launch(Path sidecarDir, Path dataDir, int heapMb) throws IOException {
        ProcessBuilder pb = new ProcessBuilder(
                "java",
                "-Xmx" + heapMb + "m",
                "-jar",
                SIDECAR_JAR_NAME,
                dataDir.toString()
        );
        pb.directory(sidecarDir.toFile());
        pb.redirectErrorStream(true);
        return pb.start();
    }
}
