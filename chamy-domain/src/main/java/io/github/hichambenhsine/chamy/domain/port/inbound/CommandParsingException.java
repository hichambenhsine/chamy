package io.github.hichambenhsine.chamy.domain.port.inbound;

public class CommandParsingException extends RuntimeException {
    public CommandParsingException(String message) {
        super(message);
    }
}