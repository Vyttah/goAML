package com.vyttah.goaml.service.report.export;

import com.vyttah.goaml.model.entity.report.Report;
import com.vyttah.goaml.service.report.ReportService;
import com.vyttah.goaml.service.report.pdf.ReportPdfService;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Unit tests for {@link DefaultReportBulkExportService}: repos/rendering mocked, zipping logic exercised. */
class DefaultReportBulkExportServiceTest {

    private final ReportService reportService = mock(ReportService.class);
    private final ReportPdfService reportPdfService = mock(ReportPdfService.class);
    private final DefaultReportBulkExportService service =
            new DefaultReportBulkExportService(reportService, reportPdfService);

    private static Report validReport(String entityReference) {
        return new Report(UUID.randomUUID(), entityReference, "DPMSR", 1, "VALID", "{}", UUID.randomUUID());
    }

    @Test
    void xmlZipBundlesEachReportsPersistedXmlByEntityReference() throws Exception {
        Report r1 = validReport("PAY-BULK-1");
        r1.setReportXml("<report>one</report>");
        Report r2 = validReport("PAY-BULK-2");
        r2.setReportXml("<report>two</report>");
        when(reportService.get(r1.getId())).thenReturn(r1);
        when(reportService.get(r2.getId())).thenReturn(r2);

        ReportBulkExportService.ZipExport zip = service.xmlZip(List.of(r1.getId(), r2.getId()));

        assertThat(zip.filename()).startsWith("dpmsr-reports-xml-").endsWith(".zip");
        Map<String, String> entries = unzipToStrings(zip.bytes());
        assertThat(entries).containsEntry("PAY-BULK-1.xml", "<report>one</report>");
        assertThat(entries).containsEntry("PAY-BULK-2.xml", "<report>two</report>");
    }

    @Test
    void pdfZipBundlesEachReportsRenderedPdf() throws Exception {
        Report r1 = validReport("PAY-BULK-3");
        UUID tenantId = UUID.randomUUID();
        when(reportService.get(r1.getId())).thenReturn(r1);
        when(reportPdfService.filed(r1.getId(), tenantId, "mlro@e2e.test"))
                .thenReturn(new ReportPdfService.PdfDocument("DPMSR_PAY-BULK-3.pdf", "pdf-bytes".getBytes(StandardCharsets.UTF_8)));

        ReportBulkExportService.ZipExport zip = service.pdfZip(List.of(r1.getId()), tenantId, "mlro@e2e.test");

        Map<String, byte[]> entries = unzip(zip.bytes());
        assertThat(entries).containsKey("DPMSR_PAY-BULK-3.pdf");
        assertThat(new String(entries.get("DPMSR_PAY-BULK-3.pdf"), StandardCharsets.UTF_8)).isEqualTo("pdf-bytes");
    }

    @Test
    void rejectsTheWholeBatchWhenAnySelectedReportIsInvalid() {
        Report valid = validReport("PAY-BULK-4");
        Report invalid = validReport("PAY-BULK-5");
        invalid.setStatus("INVALID");
        when(reportService.get(valid.getId())).thenReturn(valid);
        when(reportService.get(invalid.getId())).thenReturn(invalid);

        assertThatThrownBy(() -> service.xmlZip(List.of(valid.getId(), invalid.getId())))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("PAY-BULK-5");
    }

    private static Map<String, byte[]> unzip(byte[] zipBytes) throws Exception {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                entries.put(entry.getName(), zis.readAllBytes());
            }
        }
        return entries;
    }

    private static Map<String, String> unzipToStrings(byte[] zipBytes) throws Exception {
        Map<String, String> out = new LinkedHashMap<>();
        unzip(zipBytes).forEach((name, bytes) -> out.put(name, new String(bytes, StandardCharsets.UTF_8)));
        return out;
    }
}
