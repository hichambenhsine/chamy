package io.github.hichambenhsine.chamy.domain.port.inbound;

import java.nio.file.Path;

/**
 * Inbound Use Case Port: Launches the contextual action discovery and execution flow.
 */
public interface WorkspaceLauncher {
    int launch(Path targetDirectory);
}