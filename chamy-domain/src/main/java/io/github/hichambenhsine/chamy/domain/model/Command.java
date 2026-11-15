package io.github.hichambenhsine.chamy.domain.model;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

public record Command(
        String rawInput,
        String executable,
        List<String> arguments
) {
    public Command {
        Objects.requireNonNull(rawInput, "Raw input cannot be null.");
        Objects.requireNonNull(executable, "Executable cannot be null.");
        arguments = (arguments != null) ? List.copyOf(arguments) : Collections.emptyList();
    }
}