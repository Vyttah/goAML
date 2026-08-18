package com.vyttah.goaml.service.report.export;

import com.vyttah.goaml.model.entity.report.Report;
import com.vyttah.goaml.service.report.ReportExceptions;
import com.vyttah.goaml.service.report.ReportService;
import com.vyttah.goaml.service.report.pdf.ReportPdfService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
@RequiredArgsConstructor
public class DefaultReportBulkExportService implements ReportBulkExportService {

    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private final ReportService reportService;
    private final ReportPdfService reportPdfService;

    @Override
    public ZipExport pdfZip(List<UUID> reportIds, UUID tenantId, String userLabel) {
        List<Report> reports = resolve(reportIds);
        return zip("dpmsr-reports-pdf-" + STAMP.format(OffsetDateTime.now()) + ".zip", reports,
                report -> {
                    ReportPdfService.PdfDocument doc = reportPdfService.filed(report.getId(), tenantId, userLabel);
                    return new Entry(doc.filename(), doc.bytes());
                });
    }

    @Override
    public ZipExport xmlZip(List<UUID> reportIds) {
        List<Report> reports = resolve(reportIds);
        return zip("dpmsr-reports-xml-" + STAMP.format(OffsetDateTime.now()) + ".zip", reports,
                report -> {
                    String xml = report.getReportXml();
                    if (xml == null || xml.isBlank()) {
                        throw new ReportExceptions.ReportNotFoundException(
                                "Report has no generated XML: " + report.getId());
                    }
                    return new Entry(report.getEntityReference() + ".xml", xml.getBytes(StandardCharsets.UTF_8));
                });
    }

    private record Entry(String filename, byte[] bytes) {}

    /** Loads each id (tenant-scoped, 404 if absent) and rejects the batch if any is INVALID. */
    private List<Report> resolve(List<UUID> reportIds) {
        List<UUID> distinctIds = List.copyOf(new LinkedHashSet<>(reportIds));
        List<Report> reports = distinctIds.stream().map(reportService::get).toList();
        Set<String> invalidRefs = reports.stream()
                .filter(r -> "INVALID".equals(r.getStatus()))
                .map(Report::getEntityReference)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        if (!invalidRefs.isEmpty()) {
            throw new IllegalArgumentException(
                    "Cannot bulk-download INVALID report(s): " + String.join(", ", invalidRefs));
        }
        return reports;
    }

    private static ZipExport zip(String filename, List<Report> reports, Function<Report, Entry> render) {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(buffer)) {
            for (Report report : reports) {
                Entry entry = render.apply(report);
                zos.putNextEntry(new ZipEntry(entry.filename()));
                zos.write(entry.bytes());
                zos.closeEntry();
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to build bulk download ZIP", e);
        }
        return new ZipExport(filename, buffer.toByteArray());
    }
}
