package io.github.hichambenhsine.chamy.cli.ui;

import io.github.hichambenhsine.chamy.domain.model.ContextualAction;
import io.github.hichambenhsine.chamy.domain.model.TechStack;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ActionFilterTest {

    private final Path dummyDir = Path.of(".");
    private final List<ContextualAction> sampleActions = List.of(
            new ContextualAction(TechStack.of("GIT"), "Switch to branch: feature/login", "git checkout feature/login", dummyDir),
            new ContextualAction(TechStack.of("GIT"), "Git Status", "git status", dummyDir),
            new ContextualAction(TechStack.of("MAVEN"), "Clean & Test", "mvn clean test", dummyDir)
    );

    @Test
    @DisplayName("Should return all actions when query is null or blank")
    void shouldReturnAllOnBlankQuery() {
        assertEquals(3, ActionFilter.filter(sampleActions, "").size());
        assertEquals(3, ActionFilter.filter(sampleActions, null).size());
    }

    @Test
    @DisplayName("Should match multi-term queries across stack, description, and command")
    void shouldFilterByMultipleTerms() {
        List<ContextualAction> results = ActionFilter.filter(sampleActions, "git login");

        assertEquals(1, results.size());
        assertEquals("git checkout feature/login", results.get(0).rawCommand());
    }

    @Test
    @DisplayName("Should be case-insensitive")
    void shouldBeCaseInsensitive() {
        List<ContextualAction> results = ActionFilter.filter(sampleActions, "MAVEN TEST");

        assertEquals(1, results.size());
        assertEquals("mvn clean test", results.get(0).rawCommand());
    }
}