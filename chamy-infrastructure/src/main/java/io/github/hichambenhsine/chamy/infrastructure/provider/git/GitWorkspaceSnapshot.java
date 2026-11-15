package io.github.hichambenhsine.chamy.infrastructure.provider.git;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public record GitWorkspaceSnapshot(
        Path rootDirectory,
        String currentBranch,
        boolean hasCommits,
        Set<String> localBranches,
        List<String> unstagedOrUntrackedFiles,
        List<String> modifiedTrackedFiles,
        boolean hasStagedChanges
) {
    public GitWorkspaceSnapshot {
        Objects.requireNonNull(rootDirectory, "Root directory cannot be null.");
        localBranches = (localBranches != null) ? Set.copyOf(localBranches) : Set.of();
        unstagedOrUntrackedFiles = (unstagedOrUntrackedFiles != null) ? List.copyOf(unstagedOrUntrackedFiles) : List.of();
        modifiedTrackedFiles = (modifiedTrackedFiles != null) ? List.copyOf(modifiedTrackedFiles) : List.of();
    }
}