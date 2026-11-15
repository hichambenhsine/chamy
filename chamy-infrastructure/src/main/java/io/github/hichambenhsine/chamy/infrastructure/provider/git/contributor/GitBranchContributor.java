package io.github.hichambenhsine.chamy.infrastructure.provider.git.contributor;

import io.github.hichambenhsine.chamy.domain.model.ContextualAction;
import io.github.hichambenhsine.chamy.domain.model.WorkspaceContext;
import io.github.hichambenhsine.chamy.domain.port.outbound.ActionContributor;
import io.github.hichambenhsine.chamy.infrastructure.provider.git.GitWorkspaceProvider;
import io.github.hichambenhsine.chamy.infrastructure.provider.git.GitWorkspaceSnapshot;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class GitBranchContributor implements ActionContributor {

    @Override
    public boolean supports(WorkspaceContext context) {
        return context.getSnapshot(GitWorkspaceSnapshot.class).isPresent();
    }

    @Override
    public List<ContextualAction> discoverActions(WorkspaceContext context) {
        GitWorkspaceSnapshot snapshot = context.getSnapshot(GitWorkspaceSnapshot.class).orElseThrow();
        Path root = snapshot.rootDirectory();
        List<ContextualAction> actions = new ArrayList<>();

        actions.add(new ContextualAction(
                GitWorkspaceProvider.STACK,
                "Create & switch to new branch...",
                "git checkout -b {input}",
                root,
                "New branch name",
                60
        ));

        for (String branch : snapshot.localBranches()) {
            if (!branch.equals(snapshot.currentBranch())) {
                actions.add(new ContextualAction(
                        GitWorkspaceProvider.STACK,
                        "Switch to branch: " + branch,
                        "git checkout " + branch,
                        root,
                        50
                ));
            }
        }

        return actions;
    }

    @Override
    public List<ContextualAction> synthesizeActions(WorkspaceContext context, String query) {
        GitWorkspaceSnapshot snapshot = context.getSnapshot(GitWorkspaceSnapshot.class).orElseThrow();
        String trimmed = query.trim();
        String lower = trimmed.toLowerCase();

        String branchArg = null;
        if (lower.startsWith("checkout ") && trimmed.length() > 9) {
            branchArg = trimmed.substring(9).trim();
        } else if (lower.startsWith("switch ") && trimmed.length() > 7) {
            branchArg = trimmed.substring(7).trim();
        }

        if (branchArg == null || branchArg.isBlank() || branchArg.contains(" ") || branchArg.startsWith("-")) {
            return Collections.emptyList();
        }

        Path root = snapshot.rootDirectory();
        if (snapshot.localBranches().contains(branchArg)) {
            return List.of(new ContextualAction(
                    GitWorkspaceProvider.STACK,
                    "Switch to branch: " + branchArg,
                    "git checkout " + branchArg,
                    root,
                    980
            ));
        }

        return List.of(new ContextualAction(
                GitWorkspaceProvider.STACK,
                "Create & switch to NEW branch: " + branchArg,
                "git checkout -b " + branchArg,
                root,
                950
        ));
    }
}