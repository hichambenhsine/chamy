package io.github.hichambenhsine.chamy.domain.port.outbound;

import io.github.hichambenhsine.chamy.domain.model.TechStack;

import java.nio.file.Path;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;

/**
 * Tier-1 SPI: Detects a technology stack, extracts prompt metadata, and captures an immutable T=0 snapshot.
 */
public interface WorkspaceProvider {

    TechStack getStack();

    boolean detect(Path directory);

    default Map<String, String> extractMetadata(Path rootDirectory) {
        return Collections.emptyMap();
    }

    /**
     * Captures a tech-specific immutable state snapshot once at T=0.
     * Returned value is keyed by its concrete Class in WorkspaceContext.
     */
    default Optional<Object> captureSnapshot(Path rootDirectory) {
        return Optional.empty();
    }
}