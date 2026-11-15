package io.github.hichambenhsine.chamy.infrastructure.os.windows;

import com.sun.jna.Native;
import com.sun.jna.win32.StdCallLibrary;
import io.github.hichambenhsine.chamy.infrastructure.process.WindowsProcessExecutor;

public final class Win32ConsoleShield {

    public interface ConsoleCtrlHandler extends StdCallLibrary.StdCallCallback {
        boolean callback(int dwCtrlType);
    }

    private interface Kernel32 extends StdCallLibrary {
        Kernel32 INSTANCE = Native.load("kernel32", Kernel32.class);
        boolean SetConsoleCtrlHandler(ConsoleCtrlHandler handler, boolean add);
    }

    private static ConsoleCtrlHandler ctrlHandler;

    private Win32ConsoleShield() {}

    public static void install() {
        try {
            ctrlHandler = dwCtrlType -> {
                if (dwCtrlType == 0 || dwCtrlType == 1) {
                    WindowsProcessExecutor.killActiveProcess();
                    return true;
                }
                return false;
            };
            Kernel32.INSTANCE.SetConsoleCtrlHandler(ctrlHandler, true);
        } catch (Throwable ignored) {
        }
    }
}