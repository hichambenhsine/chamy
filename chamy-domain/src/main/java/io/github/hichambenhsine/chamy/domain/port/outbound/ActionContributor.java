package io.github.hichambenhsine.chamy.domain.port.outbound;

import io.github.hichambenhsine.chamy.domain.model.ContextualAction;
import io.github.hichambenhsine.chamy.domain.model.WorkspaceContext;

import java.util.Collections;
import java.util.List;

/**
 * Tier-2 SPI: Granular, isolated action plugin (e.g., GitBranch, GitStatus, GitCommit).
 * Adding a new feature means adding one isolated implementation without modifying existing code.
 */
public interface ActionContributor {

    boolean supports(WorkspaceContext context);

    /**
     * T=0 Discovery: Generates immediate state-driven actions from the cached snapshot.
     */
    List<ContextualAction> discoverActions(WorkspaceContext context);

    /**
     * T>0 Reactive Synthesis: Synthesizes dynamic parameterized actions in RAM as the user types.
     * Must perform ZERO disk I/O or process spawning.
     */
    default List<ContextualAction> synthesizeActions(WorkspaceContext context, String query) {
        return Collections.emptyList();
    }
}