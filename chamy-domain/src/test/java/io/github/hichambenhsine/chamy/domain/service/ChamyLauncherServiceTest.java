package io.github.hichambenhsine.chamy.domain.service;

import io.github.hichambenhsine.chamy.domain.model.Command;
import io.github.hichambenhsine.chamy.domain.model.ContextualAction;
import io.github.hichambenhsine.chamy.domain.model.TechStack;
import io.github.hichambenhsine.chamy.domain.model.WorkspaceContext;
import io.github.hichambenhsine.chamy.domain.port.inbound.CommandParser;
import io.github.hichambenhsine.chamy.domain.port.outbound.ActionContributor;
import io.github.hichambenhsine.chamy.domain.port.outbound.ContextAnalyzer;
import io.github.hichambenhsine.chamy.domain.port.outbound.LauncherPresenter;
import io.github.hichambenhsine.chamy.domain.port.outbound.ProcessExecutor;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

class ChamyLauncherServiceTest {

    private static final Path DUMMY_DIR = Path.of("project");
    private static final TechStack GIT = TechStack.of("GIT");

    @Test
    void shouldDiscoverRankSynthesizeAndExecuteParameterizedAction() {
        WorkspaceContext ctx = new WorkspaceContext(DUMMY_DIR, DUMMY_DIR, Set.of(GIT), Map.of(), Map.of());
        ContextAnalyzer analyzer = dir -> ctx;

        ActionContributor contributor = new ActionContributor() {
            @Override
            public boolean supports(WorkspaceContext context) {
                return context.detectedStacks().contains(GIT);
            }

            @Override
            public List<ContextualAction> discoverActions(WorkspaceContext context) {
                return List.of(
                        new ContextualAction(GIT, "Low priority", "git log", DUMMY_DIR, 10),
                        new ContextualAction(GIT, "High priority", "git status", DUMMY_DIR, 100)
                );
            }

            @Override
            public List<ContextualAction> synthesizeActions(WorkspaceContext context, String query) {
                if (query.startsWith("commit ")) {
                    return List.of(new ContextualAction(
                            GIT, "Commit directly", "git commit -m \"" + query.substring(7) + "\"", DUMMY_DIR, 1000
                    ));
                }
                return List.of();
            }
        };

        AtomicReference<List<ContextualAction>> capturedInitial = new AtomicReference<>();
        AtomicReference<List<ContextualAction>> capturedSynthesized = new AtomicReference<>();
        AtomicReference<Command> executedCommand = new AtomicReference<>();

        LauncherPresenter presenterMock = new LauncherPresenter() {
            @Override
            public Optional<ContextualAction> selectAction(WorkspaceContext context,
                                                           List<ContextualAction> initialActions,
                                                           Function<String, List<ContextualAction>> reactiveSynthesizer) {
                capturedInitial.set(initialActions);
                capturedSynthesized.set(reactiveSynthesizer.apply("commit fix bug"));
                return Optional.of(new ContextualAction(
                        GIT, "Ask commit", "git commit -m \"{input}\"", DUMMY_DIR, "Message", 500
                ));
            }

            @Override
            public Optional<String> promptForInput(String promptLabel) {
                return Optional.of("feat: \"clean\" arch");
            }

            @Override public void printInfo(String message) {}
            @Override public void printError(String message) {}
            @Override public void close() {}
        };

        CommandParser parserMock = raw -> new Command(raw, "git", List.of("commit"));
        ProcessExecutor executorMock = new ProcessExecutor() {
            @Override public boolean supports(String osName) { return true; }
            @Override public int execute(Command command, Path workingDirectory) {
                executedCommand.set(command);
                return 0;
            }
        };

        ChamyLauncherService service = new ChamyLauncherService(
                analyzer, List.of(contributor), presenterMock, parserMock, executorMock
        );

        int exitCode = service.launch(DUMMY_DIR);

        assertEquals(0, exitCode);
        // Vérifie le tri par score décroissant à T=0 (100 avant 10)
        assertEquals("git status", capturedInitial.get().get(0).rawCommand());
        assertEquals("git log", capturedInitial.get().get(1).rawCommand());
        // Vérifie la synthèse réactive T>0 (score 1000 avant fallback score 1)
        assertEquals("git commit -m \"fix bug\"", capturedSynthesized.get().get(0).rawCommand());
        assertEquals("commit fix bug", capturedSynthesized.get().get(1).rawCommand());
        // Vérifie l'échappement sécurisé des guillemets dans {input}
        assertEquals("git commit -m \"feat: \\\"clean\\\" arch\"", executedCommand.get().rawInput());
    }
}