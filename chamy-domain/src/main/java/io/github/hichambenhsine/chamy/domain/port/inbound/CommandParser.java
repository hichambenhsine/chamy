package io.github.hichambenhsine.chamy.domain.port.inbound;

import io.github.hichambenhsine.chamy.domain.model.Command;

public interface CommandParser {
    Command parse(String rawInput);
}