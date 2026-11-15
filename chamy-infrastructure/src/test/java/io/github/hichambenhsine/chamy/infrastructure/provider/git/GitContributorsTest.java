package io.github.hichambenhsine.chamy.infrastructure.provider.git;

import io.github.hichambenhsine.chamy.domain.model.ContextualAction;
import io.github.hichambenhsine.chamy.domain.model.WorkspaceContext;
import io.github.hichambenhsine.chamy.infrastructure.provider.git.contributor.GitBranchContributor;
import io.github.hichambenhsine.chamy.infrastructure.provider.git.contributor.GitCommitContributor;
import io.github.hichambenhsine.chamy.infrastructure.provider.git.contributor.GitStandardContributor;
import io.github.hichambenhsine.chamy.infrastructure.provider.git.contributor.GitStatusContributor;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class GitContributorsTest {

    private static final Path ROOT = Path.of("repo");

    @Test
    void shouldSuppressLogAndUseUnbornUnstageWhenNoCommitsExist() {
        GitWorkspaceSnapshot unbornSnapshot = new GitWorkspaceSnapshot(
                ROOT, "main", false, Set.of(), List.of("Main.java"), List.of(), true
        );
        WorkspaceContext ctx = new WorkspaceContext(
                ROOT, ROOT, Set.of(GitWorkspaceProvider.STACK), Map.of(),
                Map.of(GitWorkspaceSnapshot.class, unbornSnapshot)
        );

        List<ContextualAction> stdActions = new GitStandardContributor().discoverActions(ctx);
        assertEquals(1, stdActions.size());
        assertEquals("git status", stdActions.get(0).rawCommand());

        List<ContextualAction> commitActions = new GitCommitContributor().discoverActions(ctx);
        assertTrue(commitActions.stream().anyMatch(a -> a.rawCommand().equals("git rm --cached -r .")));
    }

    @Test
    void shouldSynthesizeReactiveActionsInMemoryForCommitLogBranchAndAdd() {
        GitWorkspaceSnapshot activeSnapshot = new GitWorkspaceSnapshot(
                ROOT, "main", true, Set.of("main", "dev"),
                List.of("src/App.java", "README.md"), List.of("src/App.java"), true
        );
        WorkspaceContext ctx = new WorkspaceContext(
                ROOT, ROOT, Set.of(GitWorkspaceProvider.STACK), Map.of(),
                Map.of(GitWorkspaceSnapshot.class, activeSnapshot)
        );

        // 1. T>0 Commit direct
        List<ContextualAction> commitSynth = new GitCommitContributor().synthesizeActions(ctx, "commit fix \"auth\"");
        assertEquals(1, commitSynth.size());
        assertEquals("git commit -m \"fix \\\"auth\\\"\"", commitSynth.get(0).rawCommand());

        // 2. T>0 Add filtré sur tout le snapshot
        List<ContextualAction> addSynth = new GitStatusContributor().synthesizeActions(ctx, "add app");
        assertEquals(1, addSynth.size());
        assertEquals("git add \"src/App.java\"", addSynth.get(0).rawCommand());

        // 3. T>0 Checkout existant vs création nouvelle branche
        List<ContextualAction> existingBranch = new GitBranchContributor().synthesizeActions(ctx, "checkout dev");
        assertEquals("git checkout dev", existingBranch.get(0).rawCommand());

        List<ContextualAction> newBranch = new GitBranchContributor().synthesizeActions(ctx, "checkout feature/x");
        assertEquals("git checkout -b feature/x", newBranch.get(0).rawCommand());

        // 4. T>0 Log N
        List<ContextualAction> logSynth = new GitStandardContributor().synthesizeActions(ctx, "log 25");
        assertEquals("git log --oneline -n 25", logSynth.get(0).rawCommand());
    }
}