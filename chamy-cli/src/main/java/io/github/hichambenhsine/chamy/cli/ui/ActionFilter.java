package io.github.hichambenhsine.chamy.cli.ui;

import io.github.hichambenhsine.chamy.domain.model.ContextualAction;

import java.util.Collections;
import java.util.List;

/**
 * Pure, stateless, zero-allocation multi-term action filter.
 */
public final class ActionFilter {

    private ActionFilter() {}

    public static List<ContextualAction> filter(List<ContextualAction> actions, String query) {
        if (actions == null || actions.isEmpty()) {
            return Collections.emptyList();
        }
        if (query == null || query.isBlank()) {
            return actions;
        }

        String[] terms = query.trim().toLowerCase().split("\\s+");

        return actions.stream()
                .filter(action -> matchesAllTerms(action, terms))
                .toList();
    }

    private static boolean matchesAllTerms(ContextualAction action, String[] lowerTerms) {
        String stackId = action.stack().id();
        String description = action.description();
        String rawCmd = action.rawCommand();

        for (String term : lowerTerms) {
            boolean matchFound = containsIgnoreCase(stackId, term)
                    || containsIgnoreCase(description, term)
                    || containsIgnoreCase(rawCmd, term);

            if (!matchFound) {
                return false;
            }
        }
        return true;
    }

    private static boolean containsIgnoreCase(String source, String lowerTarget) {
        if (source == null) {
            return false;
        }
        int targetLen = lowerTarget.length();
        int max = source.length() - targetLen;
        for (int i = 0; i <= max; i++) {
            if (source.regionMatches(true, i, lowerTarget, 0, targetLen)) {
                return true;
            }
        }
        return false;
    }
}