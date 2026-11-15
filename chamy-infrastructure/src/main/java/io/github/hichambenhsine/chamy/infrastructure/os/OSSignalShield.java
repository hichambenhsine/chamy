package io.github.hichambenhsine.chamy.infrastructure.os;

import io.github.hichambenhsine.chamy.infrastructure.os.windows.Win32ConsoleShield;

public final class OSSignalShield {

    private OSSignalShield() {}

    public static void install() {
        String os = System.getProperty("os.name", "").toLowerCase();
        if (os.contains("win")) {
            Win32ConsoleShield.install();
        }
    }
}