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

public class GitCommitContributor implements ActionContributor {

    @Override
    public boolean supports(WorkspaceContext context) {
        return context.getSnapshot(GitWorkspaceSnapshot.class).isPresent();
    }

    @Override
    public List<ContextualAction> discoverActions(WorkspaceContext context) {
        GitWorkspaceSnapshot snapshot = context.getSnapshot(GitWorkspaceSnapshot.class).orElseThrow();
        if (!snapshot.hasStagedChanges()) {
            return Collections.emptyList();
        }

        Path root = snapshot.rootDirectory();
        List<ContextualAction> actions = new ArrayList<>();

        actions.add(new ContextualAction(
                GitWorkspaceProvider.STACK,
                "Commit staged changes...",
                "git commit -m \"{input}\"",
                root,
                "Commit message",
                150
        ));

        if (snapshot.hasCommits()) {
            actions.add(new ContextualAction(
                    GitWorkspaceProvider.STACK,
                    "Amend last commit (no edit)",
                    "git commit --amend --no-edit",
                    root,
                    80
            ));
            actions.add(new ContextualAction(
                    GitWorkspaceProvider.STACK,
                    "Unstage ALL changes",
                    "git reset",
                    root,
                    75
            ));
        } else {
            // Angle mort éliminé : sur un repo vierge (Unborn HEAD), 'git reset' échoue
            actions.add(new ContextualAction(
                    GitWorkspaceProvider.STACK,
                    "Unstage ALL changes (initial repo)",
                    "git rm --cached -r .",
                    root,
                    75
            ));
        }

        return actions;
    }

    @Override
    public List<ContextualAction> synthesizeActions(WorkspaceContext context, String query) {
        GitWorkspaceSnapshot snapshot = context.getSnapshot(GitWorkspaceSnapshot.class).orElseThrow();
        String trimmed = query.trim();

        if (trimmed.toLowerCase().startsWith("commit ") && trimmed.length() > 7) {
            String message = trimmed.substring(7).trim();
            if (message.isBlank()) {
                return Collections.emptyList();
            }

            // Nettoyage si l'utilisateur a déjà encadré son message par des guillemets
            if (message.startsWith("\"") && message.endsWith("\"") && message.length() >= 2) {
                message = message.substring(1, message.length() - 1);
            }
            String safeMessage = message.replace("\"", "\\\"");
            Path root = snapshot.rootDirectory();

            if (snapshot.hasStagedChanges()) {
                return List.of(new ContextualAction(
                        GitWorkspaceProvider.STACK,
                        "Commit staged: \"" + message + "\"",
                        "git commit -m \"" + safeMessage + "\"",
                        root,
                        1000
                ));
            } else if (!snapshot.unstagedOrUntrackedFiles().isEmpty()) {
                return List.of(new ContextualAction(
                        GitWorkspaceProvider.STACK,
                        "Stage ALL & Commit: \"" + message + "\"",
                        "git add . && git commit -m \"" + safeMessage + "\"",
                        root,
                        900
                ));
            }
        }

        return Collections.emptyList();
    }
}