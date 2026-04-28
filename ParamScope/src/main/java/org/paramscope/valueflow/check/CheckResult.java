package org.paramscope.valueflow.check;

import java.util.ArrayList;
import java.util.List;

public final class CheckResult {
    private final CheckSeverity severity;
    private final List<String> messages;

    public CheckResult(CheckSeverity severity, List<String> messages) {
        this.severity = severity == null ? CheckSeverity.INFO : severity;
        this.messages = messages == null ? new ArrayList<>() : new ArrayList<>(messages);
    }

    public static CheckResult info(String message) {
        return new CheckResult(CheckSeverity.INFO, List.of(message));
    }

    public static CheckResult insecure(String message) {
        return new CheckResult(CheckSeverity.INSECURE, List.of(message));
    }

    public static CheckResult warning(String message) {
        return new CheckResult(CheckSeverity.WARNING, List.of(message));
    }

    public CheckSeverity severity() {
        return severity;
    }

    public List<String> messages() {
        return List.copyOf(messages);
    }
}

