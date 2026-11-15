package io.github.hichambenhsine.chamy.domain.service;

import io.github.hichambenhsine.chamy.domain.model.Command;
import io.github.hichambenhsine.chamy.domain.model.ContextualAction;
import io.github.hichambenhsine.chamy.domain.model.TechStack;
import io.github.hichambenhsine.chamy.domain.model.WorkspaceContext;
import io.github.hichambenhsine.chamy.domain.port.inbound.CommandParser;
import io.github.hichambenhsine.chamy.domain.port.inbound.WorkspaceLauncher;
import io.github.hichambenhsine.chamy.domain.port.outbound.ActionContributor;
import io.github.hichambenhsine.chamy.domain.port.outbound.ContextAnalyzer;
import io.github.hichambenhsine.chamy.domain.port.outbound.LauncherPresenter;
import io.github.hichambenhsine.chamy.domain.port.outbound.ProcessExecutor;

import java.nio.file.Path;
import java.util.*;

public class ChamyLauncherService implements WorkspaceLauncher {

    private static final TechStack SHELL_FALLBACK_STACK = TechStack.of("EXEC");

    private final ContextAnalyzer contextAnalyzer;
    private final List<ActionContributor> contributors;
    private final LauncherPresenter presenter;
    private final CommandParser commandParser;
    private final ProcessExecutor processExecutor;

    public ChamyLauncherService(ContextAnalyzer contextAnalyzer,
                                List<ActionContributor> contributors,
                                LauncherPresenter presenter,
                                CommandParser commandParser,
                                ProcessExecutor processExecutor) {
        this.contextAnalyzer = Objects.requireNonNull(contextAnalyzer, "ContextAnalyzer cannot be null.");
        this.contributors = List.copyOf(Objects.requireNonNull(contributors, "ActionContributors cannot be null."));
        this.presenter = Objects.requireNonNull(presenter, "LauncherPresenter cannot be null.");
        this.commandParser = Objects.requireNonNull(commandParser, "CommandParser cannot be null.");
        this.processExecutor = Objects.requireNonNull(processExecutor, "ProcessExecutor cannot be null.");
    }

    @Override
    public int launch(Path targetDirectory) {
        Objects.requireNonNull(targetDirectory, "Target directory cannot be null.");

        // 1. T=0 : Scan du contexte & Capture des snapshots en mémoire
        WorkspaceContext context = contextAnalyzer.scan(targetDirectory);

        List<ActionContributor> activeContributors = contributors.stream()
                .filter(c -> c.supports(context))
                .toList();

        // 2. T=0 : Découverte des actions initiales basées sur l'état réel
        List<ContextualAction> initialActions = collectAndRank(
                activeContributors.stream()
                        .flatMap(c -> c.discoverActions(context).stream())
                        .toList()
        );

        // 3. T>0 : Sélection interactive avec callback de synthèse réactive en RAM
        Optional<ContextualAction> selection = presenter.selectAction(
                context,
                initialActions,
                query -> synthesizeForQuery(context, activeContributors, query)
        );

        if (selection.isEmpty()) {
            presenter.printInfo("Cancelled.");
            return 0;
        }

        // 4. Résolution des paramètres éventuels et exécution
        ContextualAction chosen = selection.get();
        String finalRawCommand = chosen.rawCommand();

        if (chosen.requiresInput()) {
            Optional<String> userInput = presenter.promptForInput(chosen.inputPrompt());
            if (userInput.isEmpty() || userInput.get().isBlank()) {
                presenter.printInfo("Action cancelled: empty input.");
                return 0;
            }
            String sanitized = userInput.get().trim().replace("\"", "\\\"");
            finalRawCommand = finalRawCommand.replace("{input}", sanitized);
        }

        presenter.printInfo("Executing: " + finalRawCommand);
        Command parsed = commandParser.parse(finalRawCommand);
        return processExecutor.execute(parsed, chosen.executionDirectory());
    }

    /**
     * Synthétise des actions dynamiques en RAM à partir de la frappe en cours (T>0).
     */
    private List<ContextualAction> synthesizeForQuery(WorkspaceContext context,
                                                      List<ActionContributor> activeContributors,
                                                      String query) {
        if (query == null || query.isBlank()) {
            return Collections.emptyList();
        }

        List<ContextualAction> synthesized = new ArrayList<>();
        for (ActionContributor contributor : activeContributors) {
            synthesized.addAll(contributor.synthesizeActions(context, query));
        }

        // Filet de sécurité universel : l'utilisateur n'est jamais bloqué par la palette
        String trimmedQuery = query.trim();
        synthesized.add(new ContextualAction(
                SHELL_FALLBACK_STACK,
                "Run raw command: " + trimmedQuery,
                trimmedQuery,
                context.currentDirectory(),
                1 // Score minimal : apparaît toujours en dernier
        ));

        return collectAndRank(synthesized);
    }

    private List<ContextualAction> collectAndRank(List<ContextualAction> actions) {
        // Déduplication par rawCommand en gardant l'action au score le plus élevé
        Map<String, ContextualAction> deduplicated = new LinkedHashMap<>();
        for (ContextualAction action : actions) {
            deduplicated.merge(
                    action.rawCommand(),
                    action,
                    (existing, incoming) -> incoming.priorityScore() > existing.priorityScore() ? incoming : existing
            );
        }

        return deduplicated.values().stream()
                .sorted(Comparator.comparingInt(ContextualAction::priorityScore).reversed())
                .toList();
    }
}