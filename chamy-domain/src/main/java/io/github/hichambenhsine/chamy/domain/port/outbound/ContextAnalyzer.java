package io.github.hichambenhsine.chamy.domain.port.outbound;

import io.github.hichambenhsine.chamy.domain.model.WorkspaceContext;

import java.nio.file.Path;

public interface ContextAnalyzer {
    WorkspaceContext scan(Path targetDirectory);
}