package com.vyttah.goaml.engine.validation;

/**
 * Severity of a {@link ValidationMessage}. {@code ERROR} blocks submission; {@code WARNING}
 * is surfaced to the user but does not prevent building/packaging the report.
 */
public enum Severity {
    ERROR, WARNING
}
