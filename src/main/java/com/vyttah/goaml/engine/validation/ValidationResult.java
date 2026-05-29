package com.vyttah.goaml.engine.validation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Structured outcome of {@link ReportValidator}. Accumulates messages; a report is
 * {@link #isValid() valid} only when it carries no {@link Severity#ERROR} messages.
 */
public final class ValidationResult {

    private final List<ValidationMessage> messages = new ArrayList<>();

    public void add(ValidationMessage message) {
        messages.add(message);
    }

    public void error(String path, String code, String message) {
        add(ValidationMessage.error(path, code, message));
    }

    public void warning(String path, String code, String message) {
        add(ValidationMessage.warning(path, code, message));
    }

    /** True when there are no ERROR-severity messages (warnings are allowed). */
    public boolean isValid() {
        return messages.stream().noneMatch(m -> m.severity() == Severity.ERROR);
    }

    public List<ValidationMessage> messages() {
        return Collections.unmodifiableList(messages);
    }

    public List<ValidationMessage> errors() {
        return messages.stream().filter(m -> m.severity() == Severity.ERROR).toList();
    }

    public List<ValidationMessage> warnings() {
        return messages.stream().filter(m -> m.severity() == Severity.WARNING).toList();
    }

    public boolean hasCode(String code) {
        return messages.stream().anyMatch(m -> m.code().equals(code));
    }

    @Override
    public String toString() {
        return "ValidationResult{valid=" + isValid() + ", messages=" + messages + '}';
    }
}
