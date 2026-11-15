package io.github.hichambenhsine.chamy.infrastructure.parser;

import io.github.hichambenhsine.chamy.domain.model.Command;
import io.github.hichambenhsine.chamy.domain.port.inbound.CommandParser;
import io.github.hichambenhsine.chamy.domain.port.inbound.CommandParsingException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class DefaultCommandParserTest {

    private CommandParser parser;

    @BeforeEach
    void setUp() {
        parser = new DefaultCommandParser();
    }

    @Test
    void shouldParseEscapedQuotesInsideDoubleQuotes() {
        Command cmd = parser.parse("git commit -m \"fix: resolve \\\"critical\\\" bug\"");
        assertEquals("git", cmd.executable());
        assertEquals(3, cmd.arguments().size());
        assertEquals("fix: resolve \"critical\" bug", cmd.arguments().get(2));
    }

    @Test
    void shouldPreserveEmptyQuotedArguments() {
        Command cmd = parser.parse("git commit -m \"\"");
        assertEquals(3, cmd.arguments().size());
        assertEquals("", cmd.arguments().get(2));
    }

    @ParameterizedTest
    @ValueSource(strings = {"git commit -m \"unclosed", "echo 'unclosed", "cmd trailing\\"})
    void shouldThrowOnInvalidSyntax(String input) {
        assertThrows(CommandParsingException.class, () -> parser.parse(input));
    }
}