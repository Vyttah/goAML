package com.vyttah.goaml.engine.packaging;

/**
 * One file to bundle alongside the report XML in a goAML B2B submission ZIP.
 * Filename is preserved as the zip entry name (with sanitisation by the packager).
 */
public record Attachment(String filename, byte[] bytes, String contentType) {
}
