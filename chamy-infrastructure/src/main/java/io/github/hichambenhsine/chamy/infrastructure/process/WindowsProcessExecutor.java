package io.github.hichambenhsine.chamy.infrastructure.process;

import io.github.hichambenhsine.chamy.domain.model.Command;
import io.github.hichambenhsine.chamy.domain.port.outbound.ProcessExecutor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

public class WindowsProcessExecutor implements ProcessExecutor {

    private static final Set<String> KNOWN_BATCH_WRAPPERS = Set.of(
            "mvn", "mvnw", "npm", "npx", "yarn", "pnpm", "gradle", "gradlew", "ng"
    );

    private static volatile Process activeProcess = null;

    @Override
    public boolean supports(String osName) {
        return osName != null && osName.toLowerCase().contains("win");
    }

    @Override
    public int execute(Command command, Path workingDirectory) {
        validate(command, workingDirectory);

        List<String> systemTokens = buildWindowsCommandLine(command);

        ProcessBuilder pb = new ProcessBuilder(systemTokens);
        pb.directory(workingDirectory.toFile());
        pb.inheritIO();

        try {
            Process process = pb.start();
            activeProcess = process;
            return process.waitFor();
        } catch (IOException e) {
            throw new ProcessExecutionException("Failed to spawn process for: " + command.executable(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return 130;
        } finally {
            activeProcess = null;
        }
    }

    public static void killActiveProcess() {
        Process p = activeProcess;
        if (p != null && p.isAlive()) {
            p.descendants().forEach(ProcessHandle::destroyForcibly);
            p.destroyForcibly();
        }
    }

    private void validate(Command command, Path dir) {
        if (command == null || command.executable() == null || command.executable().isBlank()) {
            throw new IllegalArgumentException("Command and its executable cannot be null or blank.");
        }
        if (dir == null) {
            throw new IllegalArgumentException("Working directory cannot be null.");
        }
        if (!Files.isDirectory(dir)) {
            throw new ProcessExecutionException("Working directory is invalid or does not exist: " + dir);
        }
    }

    private List<String> buildWindowsCommandLine(Command command) {
        String raw = command.rawInput().trim();

        boolean needsBatchProtection = isBatchScript(command.executable())
                && !containsUnquotedPipeOrRedirect(raw);

        String finalCommandLine = needsBatchProtection ? raw + " < nul" : raw;
        return List.of("cmd.exe", "/c", finalCommandLine);
    }

    private boolean isBatchScript(String executable) {
        String lower = executable.toLowerCase();
        return KNOWN_BATCH_WRAPPERS.contains(lower)
                || lower.endsWith(".cmd")
                || lower.endsWith(".bat");
    }

    /**
     * Analyse lexicale légère : détecte '|' ou '<' uniquement hors guillemets.
     */
    private boolean containsUnquotedPipeOrRedirect(String raw) {
        boolean inDoubleQuotes = false;
        boolean inSingleQuotes = false;
        boolean escaped = false;

        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (escaped) {
                escaped = false;
                continue;
            }
            if (c == '\\' && !inSingleQuotes) {
                escaped = true;
                continue;
            }
            if (c == '"' && !inSingleQuotes) {
                inDoubleQuotes = !inDoubleQuotes;
            } else if (c == '\'' && !inDoubleQuotes) {
                inSingleQuotes = !inSingleQuotes;
            } else if (!inDoubleQuotes && !inSingleQuotes && (c == '|' || c == '<')) {
                return true;
            }
        }
        return false;
    }
}