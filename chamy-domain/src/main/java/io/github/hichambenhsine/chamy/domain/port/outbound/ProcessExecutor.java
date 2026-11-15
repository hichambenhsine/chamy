package io.github.hichambenhsine.chamy.domain.port.outbound;

import io.github.hichambenhsine.chamy.domain.model.Command;

import java.nio.file.Path;

public interface ProcessExecutor {

    boolean supports(String osName);

    int execute(Command command, Path workingDirectory);
}