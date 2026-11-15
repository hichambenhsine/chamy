package io.github.hichambenhsine.chamy.infrastructure.scanner;

import io.github.hichambenhsine.chamy.domain.model.TechStack;
import io.github.hichambenhsine.chamy.domain.model.WorkspaceContext;
import io.github.hichambenhsine.chamy.domain.port.outbound.ContextAnalyzer;
import io.github.hichambenhsine.chamy.domain.port.outbound.WorkspaceProvider;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class NioContextAnalyzer implements ContextAnalyzer {

    private final List<WorkspaceProvider> providers;

    public NioContextAnalyzer(List<WorkspaceProvider> providers) {
        this.providers = List.copyOf(Objects.requireNonNull(providers, "Providers cannot be null."));
    }

    @Override
    public WorkspaceContext scan(Path targetDirectory) {
        if (targetDirectory == null || !Files.isDirectory(targetDirectory)) {
            throw new IllegalArgumentException("Provided path must be a valid directory: " + targetDirectory);
        }

        Path normalizedTarget = targetDirectory.toAbsolutePath().normalize();
        Path workspaceRoot = findWorkspaceRoot(normalizedTarget);

        Set<TechStack> detectedStacks = new HashSet<>();
        Map<String, String> aggregatedMetadata = new HashMap<>();
        Map<Class<?>, Object> snapshotCache = new HashMap<>();

        for (WorkspaceProvider provider : providers) {
            if (provider.detect(workspaceRoot)) {
                detectedStacks.add(provider.getStack());
                aggregatedMetadata.putAll(provider.extractMetadata(workspaceRoot));
                provider.captureSnapshot(workspaceRoot).ifPresent(snapshot ->
                        snapshotCache.put(snapshot.getClass(), snapshot)
                );
            }
        }

        return new WorkspaceContext(
                normalizedTarget,
                workspaceRoot,
                detectedStacks,
                aggregatedMetadata,
                snapshotCache
        );
    }

    private Path findWorkspaceRoot(Path startPath) {
        Path current = startPath;
        while (current != null) {
            try {
                for (WorkspaceProvider provider : providers) {
                    if (provider.detect(current)) {
                        return current;
                    }
                }
            } catch (SecurityException e) {
                break;
            }
            current = current.getParent();
        }
        return startPath;
    }
}