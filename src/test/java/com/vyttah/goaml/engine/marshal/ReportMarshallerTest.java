package com.vyttah.goaml.engine.marshal;

import com.vyttah.goaml.domain.Report;
import com.vyttah.goaml.domain.enums.ReportCode;
import com.vyttah.goaml.domain.enums.SubmissionCode;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class ReportMarshallerTest {

    private final ReportMarshaller marshaller = new ReportMarshaller();

    @Test
    void marshalsAsUtf8WithDeclarationAndRoundTrips() {
        Report report = new Report();
        report.setRentityId(42);
        report.setSubmissionCode(SubmissionCode.E);
        report.setReportCode(ReportCode.DPMSR);
        report.setEntityReference("UNIT-TEST-001");
        report.setSubmissionDate(OffsetDateTime.of(2026, 5, 26, 9, 0, 0, 0, ZoneOffset.UTC));
        report.setCurrencyCodeLocal("AED");

        byte[] bytes = marshaller.marshal(report);
        String xml = new String(bytes, StandardCharsets.UTF_8);

        assertThat(xml).startsWith("<?xml version=\"1.0\" encoding=\"UTF-8\"");
        assertThat(xml).contains("<rentity_id>42</rentity_id>");
        assertThat(xml).contains("<report_code>DPMSR</report_code>");

        Report parsed = marshaller.unmarshal(bytes);
        assertThat(parsed.getRentityId()).isEqualTo(42);
        assertThat(parsed.getReportCode()).isEqualTo(ReportCode.DPMSR);
        assertThat(parsed.getEntityReference()).isEqualTo("UNIT-TEST-001");
    }

    @Test
    void unmarshalRejectsMalformedInput() {
        byte[] junk = "<not><a>valid</report>".getBytes(StandardCharsets.UTF_8);

        assertThat(catchThrowable(() -> marshaller.unmarshal(junk)))
                .isInstanceOf(MarshallingException.class);
    }

    private static Throwable catchThrowable(ThrowingRunnable r) {
        try { r.run(); return null; } catch (Throwable t) { return t; }
    }

    @FunctionalInterface
    private interface ThrowingRunnable { void run() throws Throwable; }
}
