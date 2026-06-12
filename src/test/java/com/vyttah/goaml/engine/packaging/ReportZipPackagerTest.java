package com.vyttah.goaml.engine.packaging;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReportZipPackagerTest {

    private final ReportZipPackager packager = new ReportZipPackager();

    @Test
    void buildsZipWithReportXmlAndAttachments() throws Exception {
        byte[] xml = "<report/>".getBytes(StandardCharsets.UTF_8);
        Attachment pdf = new Attachment("evidence.pdf",
                "PDF-CONTENT".getBytes(StandardCharsets.UTF_8), "application/pdf");

        byte[] zip = packager.zip(xml, "report.xml", List.of(pdf), PackagingLimits.UAE_DEFAULT);

        Map<String, byte[]> entries = readZip(zip);
        assertThat(entries).containsOnlyKeys("report.xml", "evidence.pdf");
        assertThat(entries.get("report.xml")).isEqualTo(xml);
        assertThat(new String(entries.get("evidence.pdf"), StandardCharsets.UTF_8))
                .isEqualTo("PDF-CONTENT");
    }

    @Test
    void rejectsAttachmentWithDisallowedExtension() {
        byte[] xml = "<report/>".getBytes(StandardCharsets.UTF_8);
        Attachment exe = new Attachment("trojan.exe",
                new byte[]{1, 2, 3}, "application/x-msdownload");

        PackagingLimits limits = new PackagingLimits(0, 0, 0, Set.of("pdf"));

        assertThatThrownBy(() -> packager.zip(xml, "report.xml", List.of(exe), limits))
                .isInstanceOf(PackagingException.class)
                .hasMessageContaining("disallowed extension");
    }

    @Test
    void rejectsAttachmentOverPerFileLimit() {
        byte[] xml = "<report/>".getBytes(StandardCharsets.UTF_8);
        byte[] big = new byte[2048];
        Attachment a = new Attachment("doc.pdf", big, "application/pdf");

        PackagingLimits limits = new PackagingLimits(0L, 1024L, 0, Set.of("pdf"));

        assertThatThrownBy(() -> packager.zip(xml, "report.xml", List.of(a), limits))
                .isInstanceOf(PackagingException.class)
                .hasMessageContaining("exceeds max");
    }

    @Test
    void rejectsTooManyAttachments() {
        byte[] xml = "<report/>".getBytes(StandardCharsets.UTF_8);
        List<Attachment> many = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            many.add(new Attachment("a" + i + ".pdf",
                    new byte[]{1}, "application/pdf"));
        }
        PackagingLimits limits = new PackagingLimits(0L, 0L, 3, Set.of("pdf"));

        assertThatThrownBy(() -> packager.zip(xml, "report.xml", many, limits))
                .isInstanceOf(PackagingException.class)
                .hasMessageContaining("Attachment count");
    }

    @Test
    void slashedEntityReferenceBecomesAFlatEntryName() throws Exception {
        // entity_reference values like "INV/2026/001" must not nest directories inside the submission ZIP
        byte[] xml = "<report/>".getBytes(StandardCharsets.UTF_8);

        byte[] zip = packager.zip(xml, "INV/2026/001.xml", List.of(), PackagingLimits.UAE_DEFAULT);

        assertThat(readZip(zip)).containsOnlyKeys("INV_2026_001.xml");
    }

    @Test
    void dotDotSegmentsCannotEscapeTheZipRoot() throws Exception {
        byte[] xml = "<report/>".getBytes(StandardCharsets.UTF_8);
        Attachment sneaky = new Attachment("../../etc/passwd.pdf",
                "X".getBytes(StandardCharsets.UTF_8), "application/pdf");

        byte[] zip = packager.zip(xml, "report.xml", List.of(sneaky), PackagingLimits.UAE_DEFAULT);

        Map<String, byte[]> entries = readZip(zip);
        assertThat(entries.keySet()).allSatisfy(name -> {
            assertThat(name).doesNotContain("/").doesNotContain("\\").doesNotContain("..");
        });
        assertThat(entries).containsKey("etc_passwd.pdf");
    }

    @Test
    void sanitizeEntryNameReplacesCollapsesAndBoundsLength() {
        assertThat(ReportZipPackager.sanitizeEntryName("PAY-1.xml")).isEqualTo("PAY-1.xml");
        assertThat(ReportZipPackager.sanitizeEntryName("a  b!!c.pdf")).isEqualTo("a_b_c.pdf");
        assertThat(ReportZipPackager.sanitizeEntryName("..")).isEqualTo("attachment");
        String longName = "x".repeat(300) + ".xml";
        String bounded = ReportZipPackager.sanitizeEntryName(longName);
        assertThat(bounded).hasSizeLessThanOrEqualTo(128).endsWith(".xml");
    }

    // ----- helpers -----

    private static Map<String, byte[]> readZip(byte[] zip) throws Exception {
        Map<String, byte[]> out = new HashMap<>();
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zip))) {
            ZipEntry e;
            while ((e = zis.getNextEntry()) != null) {
                out.put(e.getName(), zis.readAllBytes());
            }
        }
        return out;
    }
}
