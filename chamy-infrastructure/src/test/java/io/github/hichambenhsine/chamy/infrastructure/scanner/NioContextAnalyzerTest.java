package io.github.hichambenhsine.chamy.infrastructure.scanner;

import io.github.hichambenhsine.chamy.domain.model.WorkspaceContext;
import io.github.hichambenhsine.chamy.domain.port.outbound.ContextAnalyzer;
import io.github.hichambenhsine.chamy.infrastructure.provider.git.GitWorkspaceProvider;
import io.github.hichambenhsine.chamy.infrastructure.provider.git.GitWorkspaceSnapshot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class NioContextAnalyzerTest {

    private ContextAnalyzer analyzer;

    @BeforeEach
    void setUp() {
        analyzer = new NioContextAnalyzer(List.of(new GitWorkspaceProvider()));
    }

    @Test
    void shouldTreeWalkFromNestedSubfolderToGitRootAndCaptureSnapshot(@TempDir Path tempDir) throws IOException {
        Path gitDir = Files.createDirectory(tempDir.resolve(".git"));
        Files.writeString(gitDir.resolve("HEAD"), "ref: refs/heads/feature/v1\n");
        Path heads = Files.createDirectories(gitDir.resolve("refs").resolve("heads").resolve("feature"));
        Files.writeString(heads.resolve("v1"), "abc1234\n");

        Path deepDir = Files.createDirectories(tempDir.resolve("src").resolve("main").resolve("java"));

        WorkspaceContext context = analyzer.scan(deepDir);

        assertEquals(deepDir.toAbsolutePath().normalize(), context.currentDirectory());
        assertEquals(tempDir.toAbsolutePath().normalize(), context.rootDirectory());
        assertEquals("feature/v1", context.getMetadata("git.branch"));

        GitWorkspaceSnapshot snapshot = context.getSnapshot(GitWorkspaceSnapshot.class).orElseThrow();
        assertTrue(snapshot.hasCommits());
        assertTrue(snapshot.localBranches().contains("feature/v1"));
    }
}