package io.github.hichambenhsine.chamy.domain.model;

import java.nio.file.Path;
import java.util.*;

public record WorkspaceContext(
        Path currentDirectory,
        Path rootDirectory,
        Set<TechStack> detectedStacks,
        Map<String, String> metadata,
        Map<Class<?>, Object> snapshots
) {
    public WorkspaceContext {
        Objects.requireNonNull(currentDirectory, "Current directory cannot be null.");
        Objects.requireNonNull(rootDirectory, "Root directory cannot be null.");
        detectedStacks = (detectedStacks != null) ? Set.copyOf(detectedStacks) : Collections.emptySet();
        metadata = (metadata != null) ? Map.copyOf(metadata) : Collections.emptyMap();
        snapshots = (snapshots != null) ? Map.copyOf(snapshots) : Collections.emptyMap();
    }

    public String getMetadata(String key) {
        return metadata.get(key);
    }

    /**
     * Type-Safe Heterogeneous Container accessor (Effective Java, Item 33).
     * Retrieves an infrastructure-owned immutable snapshot without polluting the domain.
     */
    public <T> Optional<T> getSnapshot(Class<T> type) {
        Objects.requireNonNull(type, "Snapshot type cannot be null.");
        return Optional.ofNullable(type.cast(snapshots.get(type)));
    }
}