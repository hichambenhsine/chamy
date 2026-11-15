package io.github.hichambenhsine.chamy.cli.ui;

import io.github.hichambenhsine.chamy.domain.model.ContextualAction;
import io.github.hichambenhsine.chamy.domain.model.TechStack;
import io.github.hichambenhsine.chamy.domain.model.WorkspaceContext;
import io.github.hichambenhsine.chamy.domain.port.outbound.LauncherPresenter;
import org.jline.keymap.BindingReader;
import org.jline.keymap.KeyMap;
import org.jline.reader.EndOfFileException;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.reader.UserInterruptException;
import org.jline.terminal.Attributes;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.jline.utils.AttributedString;
import org.jline.utils.AttributedStringBuilder;
import org.jline.utils.AttributedStyle;
import org.jline.utils.InfoCmp.Capability;

import java.io.IOException;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

public class JLinePalettePresenter implements LauncherPresenter {

    private enum KeyAction {
        UP, DOWN, SELECT, CANCEL, BACKSPACE, CHAR
    }

    private static final int MAX_VISIBLE_ITEMS = 10;

    private final Terminal terminal;
    private int lastRenderedLines = 0;

    public JLinePalettePresenter() {
        try {
            this.terminal = TerminalBuilder.builder()
                    .system(true)
                    .build();
        } catch (IOException e) {
            throw new IllegalStateException("Critical failure: Cannot initialize TTY subsystem.", e);
        }
    }

    @Override
    public Optional<ContextualAction> selectAction(
            WorkspaceContext context,
            List<ContextualAction> initialActions,
            Function<String, List<ContextualAction>> reactiveSynthesizer
    ) {
        List<ContextualAction> safeInitial = (initialActions != null) ? initialActions : Collections.emptyList();
        Function<String, List<ContextualAction>> safeSynth = (reactiveSynthesizer != null)
                ? reactiveSynthesizer
                : q -> Collections.emptyList();

        Attributes originalAttributes = terminal.enterRawMode();
        try {
            return runInteractivePalette(context, safeInitial, safeSynth);
        } finally {
            clearRenderedPalette();
            terminal.setAttributes(originalAttributes);
            terminal.flush();
        }
    }

    @Override
    public Optional<String> promptForInput(String promptLabel) {
        LineReader lineReader = LineReaderBuilder.builder()
                .terminal(terminal)
                .option(LineReader.Option.DISABLE_EVENT_EXPANSION, true)
                .build();

        String formattedPrompt = new AttributedStringBuilder()
                .style(AttributedStyle.DEFAULT.foreground(AttributedStyle.YELLOW).bold())
                .append("[?] ")
                .append(promptLabel)
                .append(": ")
                .style(AttributedStyle.DEFAULT)
                .toAnsi(terminal);

        try {
            String value = lineReader.readLine(formattedPrompt);
            return Optional.ofNullable(value);
        } catch (UserInterruptException | EndOfFileException e) {
            return Optional.empty();
        }
    }

    private Optional<ContextualAction> runInteractivePalette(
            WorkspaceContext context,
            List<ContextualAction> initialActions,
            Function<String, List<ContextualAction>> reactiveSynthesizer
    ) {
        BindingReader reader = new BindingReader(terminal.reader());
        KeyMap<KeyAction> keyMap = buildKeyMap();

        StringBuilder filterQuery = new StringBuilder();
        List<ContextualAction> displayed = initialActions;
        int selectedIndex = 0;
        int scrollOffset = 0;

        while (true) {
            renderPalette(context, filterQuery.toString(), displayed, selectedIndex, scrollOffset);

            KeyAction action = reader.readBinding(keyMap);
            if (action == null) {
                continue;
            }

            switch (action) {
                case UP -> {
                    if (!displayed.isEmpty()) {
                        selectedIndex = (selectedIndex - 1 + displayed.size()) % displayed.size();
                        scrollOffset = adjustScrollOffset(selectedIndex, scrollOffset, displayed.size());
                    }
                }
                case DOWN -> {
                    if (!displayed.isEmpty()) {
                        selectedIndex = (selectedIndex + 1) % displayed.size();
                        scrollOffset = adjustScrollOffset(selectedIndex, scrollOffset, displayed.size());
                    }
                }
                case SELECT -> {
                    if (!displayed.isEmpty()) {
                        return Optional.of(displayed.get(selectedIndex));
                    }
                }
                case CANCEL -> {
                    return Optional.empty();
                }
                case BACKSPACE -> {
                    if (!filterQuery.isEmpty()) {
                        filterQuery.deleteCharAt(filterQuery.length() - 1);
                        displayed = computeDisplayedActions(initialActions, reactiveSynthesizer, filterQuery.toString());
                        selectedIndex = 0;
                        scrollOffset = 0;
                    }
                }
                case CHAR -> {
                    String lastSeq = reader.getLastBinding();
                    if (lastSeq != null && !lastSeq.isEmpty() && !Character.isISOControl(lastSeq.charAt(0))) {
                        filterQuery.append(lastSeq);
                        displayed = computeDisplayedActions(initialActions, reactiveSynthesizer, filterQuery.toString());
                        selectedIndex = 0;
                        scrollOffset = 0;
                    }
                }
            }
        }
    }

    private int adjustScrollOffset(int selectedIndex, int currentOffset, int totalSize) {
        if (selectedIndex < currentOffset) {
            return selectedIndex;
        }
        if (selectedIndex >= currentOffset + MAX_VISIBLE_ITEMS) {
            return selectedIndex - MAX_VISIBLE_ITEMS + 1;
        }
        return Math.min(currentOffset, Math.max(0, totalSize - MAX_VISIBLE_ITEMS));
    }

    private List<ContextualAction> computeDisplayedActions(
            List<ContextualAction> initialActions,
            Function<String, List<ContextualAction>> reactiveSynthesizer,
            String query
    ) {
        if (query == null || query.isBlank()) {
            return initialActions;
        }

        List<ContextualAction> filteredInitial = ActionFilter.filter(initialActions, query);
        List<ContextualAction> synthesized = reactiveSynthesizer.apply(query);

        Map<String, ContextualAction> merged = new LinkedHashMap<>();
        for (ContextualAction action : filteredInitial) {
            merged.put(action.rawCommand(), action);
        }
        for (ContextualAction action : synthesized) {
            merged.merge(
                    action.rawCommand(),
                    action,
                    (existing, incoming) -> incoming.priorityScore() > existing.priorityScore() ? incoming : existing
            );
        }

        return merged.values().stream()
                .sorted(Comparator.comparingInt(ContextualAction::priorityScore).reversed())
                .toList();
    }

    private void renderPalette(
            WorkspaceContext context,
            String query,
            List<ContextualAction> displayed,
            int selectedIndex,
            int scrollOffset
    ) {
        clearRenderedPalette();

        int termWidth = terminal.getWidth();
        int maxWidth = (termWidth > 10) ? termWidth - 1 : 80;
        int linesPrinted = 0;

        String stacks = context.detectedStacks().stream()
                .map(TechStack::id)
                .collect(Collectors.joining(", "));
        String branch = context.getMetadata("git.branch");

        AttributedStringBuilder header = new AttributedStringBuilder()
                .style(AttributedStyle.DEFAULT.foreground(AttributedStyle.CYAN).bold())
                .append("chamy");

        if (!stacks.isBlank()) {
            header.style(AttributedStyle.DEFAULT.foreground(AttributedStyle.YELLOW).bold())
                    .append(" [* ").append(stacks).append("]");
        }
        if (branch != null && !branch.isBlank()) {
            header.style(AttributedStyle.DEFAULT.foreground(AttributedStyle.MAGENTA))
                    .append(" (").append(branch).append(")");
        }

        header.style(AttributedStyle.DEFAULT)
                .append(" > ")
                .append(query);

        printTruncated(header.toAttributedString(), maxWidth);
        linesPrinted++;

        if (displayed.isEmpty()) {
            AttributedString emptyMsg = new AttributedStringBuilder()
                    .style(AttributedStyle.DEFAULT.foreground(AttributedStyle.RED))
                    .append("  Type a command to run or press ESC to cancel...")
                    .toAttributedString();
            printTruncated(emptyMsg, maxWidth);
            linesPrinted++;
        } else {
            int end = Math.min(displayed.size(), scrollOffset + MAX_VISIBLE_ITEMS);

            for (int i = scrollOffset; i < end; i++) {
                ContextualAction item = displayed.get(i);
                boolean isSelected = (i == selectedIndex);

                AttributedStringBuilder row = new AttributedStringBuilder();
                if (isSelected) {
                    row.style(AttributedStyle.DEFAULT.foreground(AttributedStyle.GREEN).bold())
                            .append("  > ");
                } else {
                    row.style(AttributedStyle.DEFAULT)
                            .append("    ");
                }

                row.style(AttributedStyle.DEFAULT.foreground(AttributedStyle.YELLOW))
                        .append("[").append(item.stack().id()).append("] ")
                        .style(isSelected ? AttributedStyle.DEFAULT.foreground(AttributedStyle.WHITE).bold() : AttributedStyle.DEFAULT)
                        .append(item.description())
                        .style(AttributedStyle.DEFAULT.faint())
                        .append(" (").append(item.rawCommand()).append(")");

                printTruncated(row.toAttributedString(), maxWidth);
                linesPrinted++;
            }
        }

        terminal.flush();
        this.lastRenderedLines = linesPrinted;
    }

    private void printTruncated(AttributedString line, int maxWidth) {
        if (line.columnLength() > maxWidth && maxWidth > 4) {
            AttributedString truncated = new AttributedStringBuilder()
                    .append(line.columnSubSequence(0, maxWidth - 3))
                    .style(AttributedStyle.DEFAULT.faint())
                    .append("...")
                    .toAttributedString();
            terminal.writer().println(truncated.toAnsi(terminal));
        } else {
            terminal.writer().println(line.toAnsi(terminal));
        }
    }

    private void clearRenderedPalette() {
        if (lastRenderedLines > 0) {
            terminal.writer().print("\u001B[" + lastRenderedLines + "A\u001B[0J");
            lastRenderedLines = 0;
        }
    }

    private KeyMap<KeyAction> buildKeyMap() {
        KeyMap<KeyAction> keyMap = new KeyMap<>();
        keyMap.setAmbiguousTimeout(100);

        keyMap.bind(KeyAction.UP, KeyMap.key(terminal, Capability.key_up), "\u001B[A", "\u001BOA");
        keyMap.bind(KeyAction.DOWN, KeyMap.key(terminal, Capability.key_down), "\u001B[B", "\u001BOB");

        keyMap.bind(KeyAction.SELECT, "\r", "\n");
        keyMap.bind(KeyAction.CANCEL, "\u001B", KeyMap.ctrl('c'), KeyMap.ctrl('d'));
        keyMap.bind(KeyAction.BACKSPACE, "\u007F", "\b", KeyMap.key(terminal, Capability.key_backspace));

        for (char c = 32; c < 127; c++) {
            keyMap.bind(KeyAction.CHAR, String.valueOf(c));
        }
        keyMap.setUnicode(KeyAction.CHAR);

        return keyMap;
    }

    @Override
    public void printInfo(String message) {
        AttributedStringBuilder asb = new AttributedStringBuilder()
                .style(AttributedStyle.DEFAULT.foreground(AttributedStyle.CYAN))
                .append("[chamy] ")
                .style(AttributedStyle.DEFAULT)
                .append(message);
        terminal.writer().println(asb.toAnsi(terminal));
        terminal.flush();
    }

    @Override
    public void printError(String message) {
        AttributedStringBuilder asb = new AttributedStringBuilder()
                .style(AttributedStyle.DEFAULT.foreground(AttributedStyle.RED).bold())
                .append("[chamy error] ")
                .append(message);
        terminal.writer().println(asb.toAnsi(terminal));
        terminal.flush();
    }

    @Override
    public void close() {
        try {
            if (terminal != null) {
                terminal.close();
            }
        } catch (IOException ignored) {
        }
    }
}