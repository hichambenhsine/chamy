package io.github.hichambenhsine.chamy.domain.model;

import java.nio.file.Path;
import java.util.Objects;

public record ContextualAction(
        TechStack stack,
        String description,
        String rawCommand,
        Path executionDirectory,
        String inputPrompt,
        int priorityScore
) {
    public ContextualAction {
        Objects.requireNonNull(stack, "Stack cannot be null.");
        Objects.requireNonNull(description, "Description cannot be null.");
        Objects.requireNonNull(rawCommand, "Raw command cannot be null.");
        Objects.requireNonNull(executionDirectory, "Execution directory cannot be null.");
    }

    public ContextualAction(TechStack stack, String description, String rawCommand, Path executionDirectory, int priorityScore) {
        this(stack, description, rawCommand, executionDirectory, null, priorityScore);
    }

    public ContextualAction(TechStack stack, String description, String rawCommand, Path executionDirectory) {
        this(stack, description, rawCommand, executionDirectory, null, 10);
    }

    public boolean requiresInput() {
        return inputPrompt != null && !inputPrompt.isBlank();
    }
}