package com.vyttah.goaml.service.attachment;

/**
 * Pluggable anti-malware hook for attachment bytes (B15). The default implementation
 * ({@link NoopAttachmentScanner}) is a no-op so the product runs today without any AV infrastructure; a real
 * implementation (ClamAV, GuardDuty, an AV sidecar) can be wired later and enabled with
 * {@code goaml.attachments.av.enabled=true} — WITHOUT requiring that infra to run in dev/test.
 *
 * <p>Content-type/magic-byte sniffing (declared-vs-actual mismatch, obvious executables) is handled
 * separately and unconditionally in {@link DefaultAttachmentService} before persist; this interface is the
 * seam for a full malware scan on the accepted bytes.
 */
public interface AttachmentScanner {

    /**
     * Scan an attachment's bytes. Implementations throw
     * {@link AttachmentExceptions.AttachmentRejectedException} if the content is malicious/unwanted; a clean
     * return means the bytes are accepted. Must be safe to call when AV is disabled (the no-op default).
     *
     * @param filename    the declared upload filename (for messages/logging)
     * @param contentType the declared MIME type
     * @param bytes       the file bytes
     */
    void scan(String filename, String contentType, byte[] bytes);
}
