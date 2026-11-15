package io.github.hichambenhsine.chamy.infrastructure.process;

import io.github.hichambenhsine.chamy.domain.model.Command;
import io.github.hichambenhsine.chamy.domain.port.outbound.ProcessExecutor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class WindowsProcessExecutorTest {

    private ProcessExecutor executor;

    @BeforeEach
    void setUp() {
        executor = new WindowsProcessExecutor();
    }

    @Test
    void shouldSupportWindowsOnly() {
        assertTrue(executor.supports("Windows 11"));
        assertFalse(executor.supports("Linux"));
    }

    @Test
    void shouldExecuteValidCommand(@TempDir Path tempDir) {
        Command cmd = new Command("java -version", "java", List.of("-version"));
        assertEquals(0, executor.execute(cmd, tempDir));
    }

    @Test
    void shouldReturnNonZeroOnUnknownCommand(@TempDir Path tempDir) {
        Command cmd = new Command("unknown-xyz-cmd", "unknown-xyz-cmd", List.of());
        assertNotEquals(0, executor.execute(cmd, tempDir));
    }
}