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

public class GitStatusContributor implements ActionContributor {

    private static final int T0_FILE_CAP = 20;

    @Override
    public boolean supports(WorkspaceContext context) {
        return context.getSnapshot(GitWorkspaceSnapshot.class).isPresent();
    }

    @Override
    public List<ContextualAction> discoverActions(WorkspaceContext context) {
        GitWorkspaceSnapshot snapshot = context.getSnapshot(GitWorkspaceSnapshot.class).orElseThrow();
        if (snapshot.unstagedOrUntrackedFiles().isEmpty()) {
            return Collections.emptyList();
        }

        List<ContextualAction> actions = new ArrayList<>();
        Path root = snapshot.rootDirectory();

        actions.add(new ContextualAction(
                GitWorkspaceProvider.STACK, "Stage ALL changes", "git add .", root, 120
        ));

        int count = 0;
        for (String file : snapshot.unstagedOrUntrackedFiles()) {
            if (count++ >= T0_FILE_CAP) break;
            actions.add(new ContextualAction(
                    GitWorkspaceProvider.STACK,
                    "Stage file: " + file,
                    "git add \"" + file + "\"",
                    root,
                    110
            ));
        }

        int diffCount = 0;
        for (String file : snapshot.modifiedTrackedFiles()) {
            if (diffCount++ >= 10) break;
            actions.add(new ContextualAction(
                    GitWorkspaceProvider.STACK,
                    "Inspect diff: " + file,
                    "git diff \"" + file + "\"",
                    root,
                    90
            ));
        }

        return actions;
    }

    @Override
    public List<ContextualAction> synthesizeActions(WorkspaceContext context, String query) {
        GitWorkspaceSnapshot snapshot = context.getSnapshot(GitWorkspaceSnapshot.class).orElseThrow();
        String trimmed = query.trim();
        String lower = trimmed.toLowerCase();
        Path root = snapshot.rootDirectory();

        List<ContextualAction> synthesized = new ArrayList<>();

        if (lower.startsWith("add ") && trimmed.length() > 4) {
            String filter = trimmed.substring(4).trim().toLowerCase();
            for (String file : snapshot.unstagedOrUntrackedFiles()) {
                if (file.toLowerCase().contains(filter)) {
                    synthesized.add(new ContextualAction(
                            GitWorkspaceProvider.STACK,
                            "Stage file: " + file,
                            "git add \"" + file + "\"",
                            root,
                            950
                    ));
                }
            }
        } else if (lower.startsWith("diff ") && trimmed.length() > 5) {
            String filter = trimmed.substring(5).trim().toLowerCase();
            for (String file : snapshot.modifiedTrackedFiles()) {
                if (file.toLowerCase().contains(filter)) {
                    synthesized.add(new ContextualAction(
                            GitWorkspaceProvider.STACK,
                            "Inspect diff: " + file,
                            "git diff \"" + file + "\"",
                            root,
                            950
                    ));
                }
            }
        }

        return synthesized;
    }
}