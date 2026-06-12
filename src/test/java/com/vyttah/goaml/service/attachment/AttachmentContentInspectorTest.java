package com.vyttah.goaml.service.attachment;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * B15 magic-byte sniffing unit tests for {@link AttachmentContentInspector} (package-private).
 */
class AttachmentContentInspectorTest {

    private void inspect(String filename, String type, byte[] bytes) {
        AttachmentContentInspector.inspect(filename, type, bytes);
    }

    @Test
    void rejectsElfPeMachOAndShebang() {
        assertThatThrownBy(() -> inspect("x.pdf", "application/pdf", new byte[]{0x7F, 'E', 'L', 'F'}))
                .isInstanceOf(AttachmentExceptions.AttachmentRejectedException.class);
        assertThatThrownBy(() -> inspect("x.pdf", "application/pdf", new byte[]{'M', 'Z', 0x00}))
                .isInstanceOf(AttachmentExceptions.AttachmentRejectedException.class);
        // Mach-O 64-bit magic 0xFEEDFACF
        assertThatThrownBy(() -> inspect("x.pdf", "application/pdf",
                new byte[]{(byte) 0xFE, (byte) 0xED, (byte) 0xFA, (byte) 0xCF}))
                .isInstanceOf(AttachmentExceptions.AttachmentRejectedException.class);
        assertThatThrownBy(() -> inspect("run.txt", "text/plain", "#!/bin/sh\necho hi".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(AttachmentExceptions.AttachmentRejectedException.class);
    }

    @Test
    void rejectsDeclaredVsActualMismatch() {
        // PDF bytes declared as an image, and PNG bytes declared as a PDF.
        assertThatThrownBy(() -> inspect("f.png", "image/png", "%PDF-1.4".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(AttachmentExceptions.AttachmentRejectedException.class);
        assertThatThrownBy(() -> inspect("f.pdf", "application/pdf",
                new byte[]{(byte) 0x89, 'P', 'N', 'G'}))
                .isInstanceOf(AttachmentExceptions.AttachmentRejectedException.class);
    }

    @Test
    void acceptsMatchingOrUnknownContent() {
        // PDF bytes declared as PDF, PNG bytes declared as image, and unknown bytes with any declared type.
        assertThatCode(() -> inspect("f.pdf", "application/pdf", "%PDF-1.7".getBytes(StandardCharsets.UTF_8)))
                .doesNotThrowAnyException();
        assertThatCode(() -> inspect("f.png", "image/png", new byte[]{(byte) 0x89, 'P', 'N', 'G'}))
                .doesNotThrowAnyException();
        assertThatCode(() -> inspect("f.txt", "text/plain", "just some text".getBytes(StandardCharsets.UTF_8)))
                .doesNotThrowAnyException();
        // empty bytes are handled upstream (size check) — inspector is a no-op
        assertThatCode(() -> inspect("f.pdf", "application/pdf", new byte[0]))
                .doesNotThrowAnyException();
    }
}
