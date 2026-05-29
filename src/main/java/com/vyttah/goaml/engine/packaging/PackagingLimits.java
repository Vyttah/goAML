package com.vyttah.goaml.engine.packaging;

import java.util.Set;

/**
 * FIU-configurable limits enforced by {@link ReportZipPackager}.
 *
 * @param maxTotalBytes        max bytes of the produced ZIP; {@code 0} = unlimited
 * @param maxAttachmentBytes   max bytes per individual attachment; {@code 0} = unlimited
 * @param maxAttachmentCount   max number of attachments; {@code 0} = unlimited
 * @param allowedExtensions    lowercase extensions without the dot (e.g. {@code "pdf"}).
 *                             Empty set means any extension is accepted.
 */
public record PackagingLimits(
        long maxTotalBytes,
        long maxAttachmentBytes,
        int maxAttachmentCount,
        Set<String> allowedExtensions) {

    public static final PackagingLimits NONE =
            new PackagingLimits(0L, 0L, 0, Set.of());

    /**
     * UAE DPMS-guide-aligned baseline: 5 MB per file, 20 MB per submission ZIP,
     * up to 50 attachments, common document types.
     */
    public static final PackagingLimits UAE_DEFAULT = new PackagingLimits(
            20L * 1024 * 1024,
            5L * 1024 * 1024,
            50,
            Set.of("pdf", "png", "jpg", "jpeg", "tif", "tiff", "doc", "docx", "xls", "xlsx", "txt"));
}
