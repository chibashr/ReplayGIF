package com.replayplugin.sidecar;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Abstraction for launching the sidecar process. Injected into SidecarManager for testability.
 */
public interface SidecarProcessLauncher {

    /**
     * Start the sidecar JVM. Working directory is sidecarDir; process runs java -Xmx{heapMb}m -jar sidecar.jar {dataDir}.
     *
     * @param sidecarDir directory containing sidecar.jar (working dir of the process)
     * @param dataDir    plugin data directory path passed as single argument to sidecar
     * @param heapMb     max heap in MB
     * @return the started process
     * @throws IOException if process fails to start
     */
    Process launch(Path sidecarDir, Path dataDir, int heapMb) throws IOException;
}
