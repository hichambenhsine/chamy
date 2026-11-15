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

public class GitStandardContributor implements ActionContributor {

    @Override
    public boolean supports(WorkspaceContext context) {
        return context.getSnapshot(GitWorkspaceSnapshot.class).isPresent();
    }

    @Override
    public List<ContextualAction> discoverActions(WorkspaceContext context) {
        GitWorkspaceSnapshot snapshot = context.getSnapshot(GitWorkspaceSnapshot.class).orElseThrow();
        Path root = snapshot.rootDirectory();
        List<ContextualAction> actions = new ArrayList<>();

        actions.add(new ContextualAction(GitWorkspaceProvider.STACK, "Git Status", "git status", root, 40));

        // Suppression du bruit : log/pull/push ne sont proposés que si au moins un commit existe
        if (snapshot.hasCommits()) {
            actions.add(new ContextualAction(GitWorkspaceProvider.STACK, "Recent Logs (10)", "git log --oneline -n 10", root, 30));
            actions.add(new ContextualAction(GitWorkspaceProvider.STACK, "Git Pull", "git pull", root, 25));
            actions.add(new ContextualAction(GitWorkspaceProvider.STACK, "Git Push", "git push", root, 20));
        }

        return actions;
    }

    @Override
    public List<ContextualAction> synthesizeActions(WorkspaceContext context, String query) {
        GitWorkspaceSnapshot snapshot = context.getSnapshot(GitWorkspaceSnapshot.class).orElseThrow();
        if (!snapshot.hasCommits()) {
            return Collections.emptyList();
        }

        String trimmed = query.trim().toLowerCase();
        if (trimmed.startsWith("log ") && trimmed.length() > 4) {
            String countStr = trimmed.substring(4).trim();
            try {
                int count = Integer.parseInt(countStr);
                if (count > 0 && count <= 500) {
                    return List.of(new ContextualAction(
                            GitWorkspaceProvider.STACK,
                            "Show last " + count + " commits",
                            "git log --oneline -n " + count,
                            snapshot.rootDirectory(),
                            950
                    ));
                }
            } catch (NumberFormatException ignored) {
            }
        }

        return Collections.emptyList();
    }
}