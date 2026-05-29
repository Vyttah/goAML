package com.vyttah.goaml.engine.validation;

/**
 * A single validation finding.
 *
 * @param severity ERROR (blocks submission) or WARNING
 * @param path     dotted location in the report tree, e.g. {@code report.transaction[0].amount_local}
 * @param code     stable machine code for the rule, e.g. {@code MANDATORY}, {@code FIU_REF_REQUIRED}
 * @param message  human-readable explanation
 */
public record ValidationMessage(Severity severity, String path, String code, String message) {

    public static ValidationMessage error(String path, String code, String message) {
        return new ValidationMessage(Severity.ERROR, path, code, message);
    }

    public static ValidationMessage warning(String path, String code, String message) {
        return new ValidationMessage(Severity.WARNING, path, code, message);
    }
}
