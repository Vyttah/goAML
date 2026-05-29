package com.vyttah.goaml.engine.packaging;

import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Builds the multipart-submission ZIP expected by goAML Web's
 * {@code POST /api/Reports/PostReport}: one report XML + zero-or-more attachments.
 *
 * <p>Limits ({@link PackagingLimits}) are enforced up front so we never push a bag that
 * the FIU will reject for size / count / extension reasons.
 */
@Component
public class ReportZipPackager {

    public byte[] zip(byte[] reportXml, String reportFilename,
                      List<Attachment> attachments, PackagingLimits limits) {
        List<Attachment> safeAttachments = attachments == null ? List.of() : attachments;

        if (reportXml == null || reportXml.length == 0) {
            throw new PackagingException("reportXml is empty");
        }
        if (reportFilename == null || reportFilename.isBlank()) {
            throw new PackagingException("reportFilename is blank");
        }
        if (limits.maxAttachmentCount() > 0 && safeAttachments.size() > limits.maxAttachmentCount()) {
            throw new PackagingException("Attachment count " + safeAttachments.size()
                    + " exceeds max " + limits.maxAttachmentCount());
        }
        for (Attachment a : safeAttachments) {
            checkAttachment(a, limits);
        }

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            writeEntry(zos, reportFilename, reportXml);
            for (Attachment a : safeAttachments) {
                writeEntry(zos, a.filename(), a.bytes());
            }
        } catch (IOException e) {
            throw new PackagingException("Failed to write ZIP", e);
        }

        byte[] zipped = baos.toByteArray();
        if (limits.maxTotalBytes() > 0 && zipped.length > limits.maxTotalBytes()) {
            throw new PackagingException("ZIP size " + zipped.length
                    + " exceeds max " + limits.maxTotalBytes());
        }
        return zipped;
    }

    private static void checkAttachment(Attachment a, PackagingLimits limits) {
        if (a == null || a.filename() == null || a.filename().isBlank()) {
            throw new PackagingException("Attachment has blank filename");
        }
        if (a.bytes() == null || a.bytes().length == 0) {
            throw new PackagingException("Attachment " + a.filename() + " is empty");
        }
        if (limits.maxAttachmentBytes() > 0 && a.bytes().length > limits.maxAttachmentBytes()) {
            throw new PackagingException("Attachment " + a.filename() + " size "
                    + a.bytes().length + " exceeds max " + limits.maxAttachmentBytes());
        }
        if (!limits.allowedExtensions().isEmpty()) {
            String ext = extensionOf(a.filename());
            if (!limits.allowedExtensions().contains(ext)) {
                throw new PackagingException("Attachment " + a.filename()
                        + " has disallowed extension '" + ext + "'");
            }
        }
    }

    private static void writeEntry(ZipOutputStream zos, String name, byte[] body) throws IOException {
        zos.putNextEntry(new ZipEntry(name));
        zos.write(body);
        zos.closeEntry();
    }

    private static String extensionOf(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot < 0 || dot == filename.length() - 1
                ? ""
                : filename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
