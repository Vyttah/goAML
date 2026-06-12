package com.vyttah.goaml.service.attachment;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Default {@link AttachmentScanner}: accepts every file (no malware scan). Active unless
 * {@code goaml.attachments.av.enabled=true}, which is the default — so the product runs with no AV infra
 * today. A real scanner bean (annotated {@code @ConditionalOnProperty(... havingValue="true")}) replaces it
 * in production once ClamAV/GuardDuty is wired.
 *
 * <p>This is NOT the only content guard: {@link DefaultAttachmentService} always sniffs magic bytes and
 * rejects declared-vs-actual mismatches and obvious executables, AV on or off.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "goaml.attachments.av.enabled", havingValue = "false", matchIfMissing = true)
public class NoopAttachmentScanner implements AttachmentScanner {

    @Override
    public void scan(String filename, String contentType, byte[] bytes) {
        // No malware scanning is wired. Magic-byte sniffing in DefaultAttachmentService still applies.
        log.debug("AV disabled — skipping malware scan for attachment {}", filename);
    }
}
