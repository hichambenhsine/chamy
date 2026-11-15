package io.github.hichambenhsine.chamy.infrastructure.parser;

import io.github.hichambenhsine.chamy.domain.port.inbound.CommandParsingException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class CommandLineTokenizer {

    private enum State {
        NORMAL,
        IN_DOUBLE_QUOTES,
        IN_SINGLE_QUOTES
    }

    private CommandLineTokenizer() {}

    public static List<String> tokenize(String input) {
        if (input == null || input.isBlank()) {
            return Collections.emptyList();
        }

        List<String> tokens = new ArrayList<>();
        StringBuilder currentToken = new StringBuilder();
        State state = State.NORMAL;
        boolean isEscaped = false;
        boolean hasTokenContent = false;

        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);

            if (isEscaped) {
                currentToken.append(c);
                hasTokenContent = true;
                isEscaped = false;
                continue;
            }

            if (c == '\\' && state != State.IN_SINGLE_QUOTES) {
                isEscaped = true;
                continue;
            }

            switch (state) {
                case NORMAL -> {
                    if (c == '"') {
                        state = State.IN_DOUBLE_QUOTES;
                        hasTokenContent = true;
                    } else if (c == '\'') {
                        state = State.IN_SINGLE_QUOTES;
                        hasTokenContent = true;
                    } else if (Character.isWhitespace(c)) {
                        if (hasTokenContent) {
                            tokens.add(currentToken.toString());
                            currentToken.setLength(0);
                            hasTokenContent = false;
                        }
                    } else {
                        currentToken.append(c);
                        hasTokenContent = true;
                    }
                }
                case IN_DOUBLE_QUOTES -> {
                    if (c == '"') {
                        state = State.NORMAL;
                    } else {
                        currentToken.append(c);
                    }
                }
                case IN_SINGLE_QUOTES -> {
                    if (c == '\'') {
                        state = State.NORMAL;
                    } else {
                        currentToken.append(c);
                    }
                }
            }
        }

        if (isEscaped) {
            throw new CommandParsingException("Dangling backslash escape character at end of command.");
        }
        if (state == State.IN_DOUBLE_QUOTES) {
            throw new CommandParsingException("Syntax error: Unclosed double quote (\") detected.");
        }
        if (state == State.IN_SINGLE_QUOTES) {
            throw new CommandParsingException("Syntax error: Unclosed single quote (') detected.");
        }

        if (hasTokenContent) {
            tokens.add(currentToken.toString());
        }

        return Collections.unmodifiableList(tokens);
    }
}