package io.github.hichambenhsine.chamy.infrastructure.parser;

import io.github.hichambenhsine.chamy.domain.model.Command;
import io.github.hichambenhsine.chamy.domain.port.inbound.CommandParser;

import java.util.List;

public class DefaultCommandParser implements CommandParser {

    @Override
    public Command parse(String rawInput) {
        if (rawInput == null || rawInput.isBlank()) {
            return new Command("", "", List.of());
        }

        List<String> tokens = CommandLineTokenizer.tokenize(rawInput.trim());
        if (tokens.isEmpty()) {
            return new Command(rawInput, "", List.of());
        }

        String executable = tokens.get(0);
        List<String> arguments = tokens.size() > 1
                ? List.copyOf(tokens.subList(1, tokens.size()))
                : List.of();

        return new Command(rawInput, executable, arguments);
    }
}