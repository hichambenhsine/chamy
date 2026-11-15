package io.github.hichambenhsine.chamy.infrastructure.provider.git;

import io.github.hichambenhsine.chamy.domain.model.TechStack;
import io.github.hichambenhsine.chamy.domain.port.outbound.WorkspaceProvider;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

public class GitWorkspaceProvider implements WorkspaceProvider {

    public static final TechStack STACK = TechStack.of("GIT");
    private static final int MAX_SNAPSHOT_FILES = 500;

    @Override
    public TechStack getStack() {
        return STACK;
    }

    @Override
    public boolean detect(Path directory) {
        if (directory == null) {
            return false;
        }
        Path gitPath = directory.resolve(".git");
        return Files.isDirectory(gitPath) || Files.isRegularFile(gitPath);
    }

    @Override
    public Map<String, String> extractMetadata(Path rootDirectory) {
        String branch = extractGitBranch(rootDirectory);
        if (branch != null && !branch.isBlank()) {
            return Map.of("git.branch", branch);
        }
        return Collections.emptyMap();
    }

    @Override
    public Optional<Object> captureSnapshot(Path rootDirectory) {
        Path gitDir = resolveGitDir(rootDirectory);
        if (gitDir == null) {
            return Optional.empty();
        }

        String currentBranch = extractGitBranch(rootDirectory);
        Set<String> localBranches = scanLocalBranches(gitDir);
        boolean hasCommits = !localBranches.isEmpty() || isDetachedCommitHead(gitDir);

        List<String> unstaged = new ArrayList<>();
        List<String> modifiedTracked = new ArrayList<>();
        boolean hasStaged = inspectWorkingTree(rootDirectory, unstaged, modifiedTracked);

        return Optional.of(new GitWorkspaceSnapshot(
                rootDirectory,
                currentBranch,
                hasCommits,
                localBranches,
                unstaged,
                modifiedTracked,
                hasStaged
        ));
    }

    private boolean inspectWorkingTree(Path workspaceRoot, List<String> unstaged, List<String> modifiedTracked) {
        boolean hasStaged = false;
        ProcessBuilder pb = new ProcessBuilder("git", "-c", "core.quotePath=false", "status", "--porcelain");
        pb.directory(workspaceRoot.toFile());
        pb.redirectErrorStream(false);

        Process process = null;
        try {
            process = pb.start();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                int linesRead = 0;
                while ((line = reader.readLine()) != null && linesRead++ < MAX_SNAPSHOT_FILES) {
                    if (line.length() < 4) continue;

                    char indexStatus = line.charAt(0);
                    char workTreeStatus = line.charAt(1);
                    String filePath = line.substring(3);

                    if (indexStatus == 'R' || workTreeStatus == 'R') {
                        int arrowIdx = filePath.indexOf(" -> ");
                        if (arrowIdx >= 0) {
                            filePath = filePath.substring(arrowIdx + 4);
                        }
                    }

                    if (filePath.startsWith("\"") && filePath.endsWith("\"") && filePath.length() >= 2) {
                        filePath = filePath.substring(1, filePath.length() - 1);
                    }

                    if (indexStatus != ' ' && indexStatus != '?') {
                        hasStaged = true;
                    }
                    if (workTreeStatus != ' ') {
                        unstaged.add(filePath);
                        if (workTreeStatus != '?') {
                            modifiedTracked.add(filePath);
                        }
                    }
                }
            }

            if (!process.waitFor(2, TimeUnit.SECONDS)) {
                process.destroyForcibly();
            }
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
        } finally {
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
            }
        }
        return hasStaged;
    }

    private Set<String> scanLocalBranches(Path gitDir) {
        Set<String> branches = new TreeSet<>();
        Path headsDir = gitDir.resolve("refs").resolve("heads");
        if (Files.isDirectory(headsDir)) {
            try (Stream<Path> stream = Files.walk(headsDir, 10)) {
                stream.filter(Files::isRegularFile)
                        .forEach(p -> branches.add(headsDir.relativize(p).toString().replace('\\', '/')));
            } catch (IOException ignored) {
            }
        }

        Path packedRefsFile = gitDir.resolve("packed-refs");
        if (Files.isRegularFile(packedRefsFile)) {
            try (BufferedReader reader = Files.newBufferedReader(packedRefsFile)) {
                String line;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (line.startsWith("#") || line.startsWith("^")) continue;
                    int spaceIdx = line.indexOf(' ');
                    if (spaceIdx > 0) {
                        String ref = line.substring(spaceIdx + 1);
                        if (ref.startsWith("refs/heads/")) {
                            branches.add(ref.substring("refs/heads/".length()));
                        }
                    }
                }
            } catch (IOException ignored) {
            }
        }
        return branches;
    }

    private boolean isDetachedCommitHead(Path gitDir) {
        Path headFile = gitDir.resolve("HEAD");
        if (!Files.isRegularFile(headFile)) return false;
        try {
            String content = Files.readString(headFile).trim();
            return !content.startsWith("ref:") && !content.isEmpty();
        } catch (IOException e) {
            return false;
        }
    }

    private Path resolveGitDir(Path workspaceRoot) {
        Path gitPath = workspaceRoot.resolve(".git");
        if (!Files.exists(gitPath)) return null;
        try {
            if (Files.isRegularFile(gitPath)) {
                String content = Files.readString(gitPath).trim();
                if (content.startsWith("gitdir:")) {
                    return workspaceRoot.resolve(content.substring(7).trim()).normalize();
                }
                return null;
            }
            return gitPath;
        } catch (IOException e) {
            return null;
        }
    }

    private String extractGitBranch(Path workspaceRoot) {
        Path gitDir = resolveGitDir(workspaceRoot);
        if (gitDir == null) return null;
        Path headFile = gitDir.resolve("HEAD");
        if (!Files.isRegularFile(headFile)) return null;
        try {
            String content = Files.readString(headFile).trim();
            if (content.startsWith("ref:")) {
                String ref = content.substring(4).trim();
                return ref.startsWith("refs/heads/") ? ref.substring(11) : ref;
            }
            return content.length() > 7 ? content.substring(0, 7) : content;
        } catch (IOException e) {
            return null;
        }
    }
}