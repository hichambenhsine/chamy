package io.github.hichambenhsine.chamy.domain.port.outbound;

import io.github.hichambenhsine.chamy.domain.model.ContextualAction;
import io.github.hichambenhsine.chamy.domain.model.WorkspaceContext;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;

public interface LauncherPresenter extends AutoCloseable {

    /**
     * Presents the Command Palette.
     *
     * @param context            active immutable workspace context
     * @param initialActions     T=0 actions sorted by priority
     * @param reactiveSynthesizer T>0 callback synthesizing dynamic actions from the user's live query
     */
    Optional<ContextualAction> selectAction(
            WorkspaceContext context,
            List<ContextualAction> initialActions,
            Function<String, List<ContextualAction>> reactiveSynthesizer
    );

    Optional<String> promptForInput(String promptLabel);

    void printInfo(String message);

    void printError(String message);

    @Override
    void close();
}