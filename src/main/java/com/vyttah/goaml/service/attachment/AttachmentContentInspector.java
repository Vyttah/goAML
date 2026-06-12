package com.vyttah.goaml.service.attachment;

import java.util.Locale;

/**
 * B15 content sniffing: inspects the leading bytes of an upload to reject (a) obvious executables —
 * PE (Windows {@code MZ}), ELF (Linux), Mach-O (macOS), and script shebangs ({@code #!}) — and (b) a
 * declared-content-type that contradicts the actual bytes (a renamed/relabelled executable claiming to be a
 * PDF/image). This runs BEFORE persist, AV on or off, so a renamed binary never enters the FIU submission ZIP.
 *
 * <p>Deliberately conservative: it does NOT try to verify every allowed type, only to catch the dangerous
 * cases. Unknown-but-harmless content passes (the extension/size allow-list in
 * {@link DefaultAttachmentService} already bounds what is accepted).
 */
final class AttachmentContentInspector {

    private AttachmentContentInspector() {}

    // Magic numbers (first bytes) of known-dangerous content.
    private static final byte[] ELF_MAGIC = {0x7F, 'E', 'L', 'F'};
    private static final byte[] PE_MAGIC = {'M', 'Z'};                 // DOS/PE executables (.exe/.dll)
    private static final byte[] SHEBANG = {'#', '!'};                  // #! script
    // Mach-O magics (32/64-bit, both endiannesses) + the universal/"fat" binary magic.
    private static final int[] MACHO_MAGICS = {
            0xFEEDFACE, 0xFEEDFACF, 0xCEFAEDFE, 0xCFFAEDFE, 0xCAFEBABE, 0xBEBAFECA
    };

    // Magic numbers of content we positively recognise, to catch declared-vs-actual mismatches.
    private static final byte[] PDF_MAGIC = {'%', 'P', 'D', 'F'};
    private static final byte[] PNG_MAGIC = {(byte) 0x89, 'P', 'N', 'G'};
    private static final byte[] JPEG_MAGIC = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final byte[] GIF_MAGIC = {'G', 'I', 'F', '8'};

    /**
     * @throws AttachmentExceptions.AttachmentRejectedException if the bytes are an executable, or the actual
     *         content type plainly contradicts the declared MIME type.
     */
    static void inspect(String filename, String declaredContentType, byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return; // emptiness is handled by the size check in DefaultAttachmentService
        }

        // (a) Reject obvious executables outright, regardless of the declared type or extension.
        if (startsWith(bytes, ELF_MAGIC) || startsWith(bytes, PE_MAGIC)
                || startsWith(bytes, SHEBANG) || isMachO(bytes)) {
            throw new AttachmentExceptions.AttachmentRejectedException(
                    "Attachment " + filename + " is an executable/script and cannot be uploaded");
        }

        // (b) Declared-vs-actual mismatch: if we positively recognise the bytes as a different family than
        //     the declared MIME type claims, reject (a relabelled file). Only fires when we both recognise
        //     the bytes AND the declared type names a conflicting family — unknown bytes pass.
        String declared = declaredContentType == null ? "" : declaredContentType.toLowerCase(Locale.ROOT);
        ActualType actual = sniff(bytes);
        if (actual != ActualType.UNKNOWN && declaredConflictsWith(declared, actual)) {
            throw new AttachmentExceptions.AttachmentRejectedException(
                    "Attachment " + filename + " declares content-type '" + declaredContentType
                            + "' but its content is " + actual.label + " — declared/actual type mismatch");
        }
    }

    private enum ActualType {
        PDF("a PDF"), PNG("a PNG image"), JPEG("a JPEG image"), GIF("a GIF image"), UNKNOWN("unknown");

        final String label;

        ActualType(String label) {
            this.label = label;
        }
    }

    private static ActualType sniff(byte[] bytes) {
        if (startsWith(bytes, PDF_MAGIC)) return ActualType.PDF;
        if (startsWith(bytes, PNG_MAGIC)) return ActualType.PNG;
        if (startsWith(bytes, JPEG_MAGIC)) return ActualType.JPEG;
        if (startsWith(bytes, GIF_MAGIC)) return ActualType.GIF;
        return ActualType.UNKNOWN;
    }

    /** True when the declared MIME type names a family the actual bytes contradict. */
    private static boolean declaredConflictsWith(String declared, ActualType actual) {
        if (declared.isBlank()) {
            return false; // no declared type to contradict
        }
        boolean declaresPdf = declared.contains("pdf");
        boolean declaresImage = declared.contains("image");
        return switch (actual) {
            case PDF -> declaresImage;                 // bytes are a PDF but caller said image/*
            case PNG, JPEG, GIF -> declaresPdf;        // bytes are an image but caller said application/pdf
            case UNKNOWN -> false;
        };
    }

    private static boolean isMachO(byte[] bytes) {
        if (bytes.length < 4) {
            return false;
        }
        int magic = ((bytes[0] & 0xFF) << 24) | ((bytes[1] & 0xFF) << 16)
                | ((bytes[2] & 0xFF) << 8) | (bytes[3] & 0xFF);
        for (int m : MACHO_MAGICS) {
            if (magic == m) {
                return true;
            }
        }
        return false;
    }

    private static boolean startsWith(byte[] bytes, byte[] prefix) {
        if (bytes.length < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if (bytes[i] != prefix[i]) {
                return false;
            }
        }
        return true;
    }
}
