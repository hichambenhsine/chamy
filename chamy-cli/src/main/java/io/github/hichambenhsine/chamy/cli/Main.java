package io.github.hichambenhsine.chamy.cli;

import io.github.hichambenhsine.chamy.cli.ui.JLinePalettePresenter;
import io.github.hichambenhsine.chamy.domain.port.inbound.CommandParser;
import io.github.hichambenhsine.chamy.domain.port.inbound.WorkspaceLauncher;
import io.github.hichambenhsine.chamy.domain.port.outbound.*;
import io.github.hichambenhsine.chamy.domain.service.ChamyLauncherService;
import io.github.hichambenhsine.chamy.infrastructure.os.OSSignalShield;
import io.github.hichambenhsine.chamy.infrastructure.parser.DefaultCommandParser;
import io.github.hichambenhsine.chamy.infrastructure.scanner.NioContextAnalyzer;

import java.nio.file.Path;
import java.util.List;
import java.util.ServiceLoader;

/**
 * Composition Root for Chamy v1.0.0.
 * Zero hardcoded factories: discovers Providers, ActionContributors, and OS ProcessExecutors via SPI.
 */
public class Main {

    public static void main(String[] args) {
        OSSignalShield.install();

        Path targetDirectory = (args.length > 0)
                ? Path.of(args[0]).toAbsolutePath().normalize()
                : Path.of(".").toAbsolutePath().normalize();

        // 1. Chargement dynamique des 3 SPIs
        List<WorkspaceProvider> providers = loadSpi(WorkspaceProvider.class);
        List<ActionContributor> contributors = loadSpi(ActionContributor.class);
        String osName = System.getProperty("os.name", "");

        ProcessExecutor processExecutor = loadSpi(ProcessExecutor.class).stream()
                .filter(executor -> executor.supports(osName))
                .findFirst()
                .orElseThrow(() -> new UnsupportedOperationException(
                        "No ProcessExecutor SPI found for Operating System: " + osName
                ));

        // 2. Assemblage Hexagonal Pur
        ContextAnalyzer contextAnalyzer = new NioContextAnalyzer(providers);
        CommandParser commandParser = new DefaultCommandParser();

        int exitCode = 0;
        try (LauncherPresenter presenter = new JLinePalettePresenter()) {

            WorkspaceLauncher launcher = new ChamyLauncherService(
                    contextAnalyzer,
                    contributors,
                    presenter,
                    commandParser,
                    processExecutor
            );

            exitCode = launcher.launch(targetDirectory);

        } catch (Exception e) {
            System.err.println("Fatal execution error: " + e.getMessage());
            exitCode = 1;
        }

        System.exit(exitCode);
    }

    private static <T> List<T> loadSpi(Class<T> serviceType) {
        return ServiceLoader.load(serviceType)
                .stream()
                .map(ServiceLoader.Provider::get)
                .toList();
    }
}