package com.vyttah.goaml.service.ingestion;

import com.vyttah.goaml.config.ingestion.IngestionProperties;
import com.vyttah.goaml.model.dto.report.DpmsrCreateRequest;
import com.vyttah.goaml.service.ingestion.IngestionExceptions.ImportRejectedException;
import com.vyttah.goaml.service.report.ReportExceptions;
import com.vyttah.goaml.service.report.ReportResult;
import com.vyttah.goaml.service.report.ReportService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link CsvImporter}: {@link ReportService} mocked. Covers good-row creation, per-row
 * isolation (bad value / duplicate), PERSON vs ENTITY mapping, and the whole-file rejections (missing
 * required headers, over the row cap).
 */
class CsvImporterTest {

    private static final String HEADER = "entity_reference,submission_date,fiu_ref_number,reason,action,"
            + "indicators,reporting_person_first_name,reporting_person_last_name,party_type,party_reason,"
            + "person_first_name,person_last_name,person_birthdate,person_nationality,person_id_number,"
            + "entity_name,entity_incorporation_number,entity_incorporation_country,"
            + "good_item_type,good_description,good_estimated_value,good_currency_code,good_status_code";

    private final ReportService reportService = mock(ReportService.class);

    private final UUID tenantId = UUID.randomUUID();
    private final UUID actor = UUID.randomUUID();

    private CsvImporter importer(int maxRows) {
        return new CsvImporter(reportService, new IngestionProperties(maxRows));
    }

    private byte[] csv(String... rows) {
        return (HEADER + "\n" + String.join("\n", rows) + "\n").getBytes(StandardCharsets.UTF_8);
    }

    @Test
    void twoGoodPersonRowsCreateTwoReports() {
        when(reportService.create(any(), eq(tenantId), eq(actor)))
                .thenReturn(new ReportResult(UUID.randomUUID(), "VALID", List.of()),
                        new ReportResult(UUID.randomUUID(), "VALID", List.of()));

        List<ImportRowResult> results = importer(500).importCsv(csv(
                "REF-1,2026-05-26,,cash sale,A,IND1;IND2,Sara,Khan,PERSON,buyer,John,Doe,1990-01-01,AE,784,,,,GOLD,1kg bar,60000,AED,SOLD",
                "REF-2,2026-05-27,,cash sale,A,,Sara,Khan,PERSON,buyer,Jane,Roe,,AE,785,,,,GOLD,coin,55000,AED,SOLD"
        ), tenantId, actor);

        assertThat(results).hasSize(2);
        assertThat(results).allMatch(r -> r.status().equals("VALID") && r.reportCreated());
        assertThat(results).extracting(ImportRowResult::entityReference).containsExactly("REF-1", "REF-2");
        verify(reportService, times(2)).create(any(), eq(tenantId), eq(actor));
    }

    @Test
    void personRowMapsFieldsOntoRequest() {
        when(reportService.create(any(), any(), any()))
                .thenReturn(new ReportResult(UUID.randomUUID(), "VALID", List.of()));

        importer(500).importCsv(csv(
                "REF-1,2026-05-26,FIU-9,cash sale,A,IND1;IND2,Sara,Khan,PERSON,buyer,John,Doe,1990-01-01,AE,784,,,,GOLD,1kg bar,60000.50,AED,SOLD"
        ), tenantId, actor);

        ArgumentCaptor<DpmsrCreateRequest> req = ArgumentCaptor.forClass(DpmsrCreateRequest.class);
        verify(reportService).create(req.capture(), any(), any());
        DpmsrCreateRequest r = req.getValue();
        assertThat(r.entityReference()).isEqualTo("REF-1");
        assertThat(r.fiuRefNumber()).isEqualTo("FIU-9");
        assertThat(r.indicators()).containsExactly("IND1", "IND2");
        assertThat(r.reportingPerson().firstName()).isEqualTo("Sara");
        assertThat(r.parties()).hasSize(1);
        assertThat(r.parties().get(0).person().firstName()).isEqualTo("John");
        assertThat(r.parties().get(0).person().idNumber()).isEqualTo("784");
        assertThat(r.parties().get(0).entity()).isNull();
        assertThat(r.goods()).hasSize(1);
        assertThat(r.goods().get(0).itemType()).isEqualTo("GOLD");
        assertThat(r.goods().get(0).estimatedValue()).isEqualByComparingTo(new BigDecimal("60000.50"));
    }

    @Test
    void entityRowMapsEntityParty() {
        when(reportService.create(any(), any(), any()))
                .thenReturn(new ReportResult(UUID.randomUUID(), "VALID", List.of()));

        importer(500).importCsv(csv(
                "REF-E,2026-05-26,,cash sale,A,,Sara,Khan,ENTITY,seller,,,,,,Gold Traders LLC,CN-123,AE,GOLD,bars,90000,AED,SOLD"
        ), tenantId, actor);

        ArgumentCaptor<DpmsrCreateRequest> req = ArgumentCaptor.forClass(DpmsrCreateRequest.class);
        verify(reportService).create(req.capture(), any(), any());
        DpmsrCreateRequest.Party party = req.getValue().parties().get(0);
        assertThat(party.entity().name()).isEqualTo("Gold Traders LLC");
        assertThat(party.entity().incorporationNumber()).isEqualTo("CN-123");
        assertThat(party.person()).isNull();
    }

    @Test
    void badNumberRowFailsButOthersContinue() {
        when(reportService.create(any(), any(), any()))
                .thenReturn(new ReportResult(UUID.randomUUID(), "VALID", List.of()));

        List<ImportRowResult> results = importer(500).importCsv(csv(
                "REF-1,2026-05-26,,,,,Sara,Khan,PERSON,,John,Doe,,,,,,,GOLD,bar,NOT_A_NUMBER,AED,SOLD",
                "REF-2,2026-05-27,,,,,Sara,Khan,PERSON,,Jane,Roe,,,,,,,GOLD,coin,55000,AED,SOLD"
        ), tenantId, actor);

        assertThat(results.get(0).status()).isEqualTo("FAILED");
        assertThat(results.get(0).errors().get(0)).contains("good_estimated_value");
        assertThat(results.get(1).status()).isEqualTo("VALID");
        verify(reportService, times(1)).create(any(), any(), any()); // only the good row reached create
    }

    @Test
    void duplicateRowFails() {
        when(reportService.create(any(), any(), any()))
                .thenThrow(new ReportExceptions.DuplicateEntityReferenceException("dup"));

        List<ImportRowResult> results = importer(500).importCsv(csv(
                "REF-DUP,2026-05-26,,,,,Sara,Khan,PERSON,,John,Doe,,,,,,,GOLD,bar,60000,AED,SOLD"
        ), tenantId, actor);

        assertThat(results.get(0).status()).isEqualTo("FAILED");
        assertThat(results.get(0).errors().get(0)).contains("Duplicate");
    }

    @Test
    void missingRequiredHeaderRejectsFile() {
        // header without good_item_type / good_estimated_value
        byte[] bad = ("entity_reference,submission_date,party_type,reporting_person_first_name,"
                + "reporting_person_last_name\nREF-1,2026-05-26,PERSON,Sara,Khan\n").getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> importer(500).importCsv(bad, tenantId, actor))
                .isInstanceOf(ImportRejectedException.class)
                .hasMessageContaining("missing required columns");
        verify(reportService, never()).create(any(), any(), any());
    }

    @Test
    void overRowCapRejectsFile() {
        assertThatThrownBy(() -> importer(1).importCsv(csv(
                "REF-1,2026-05-26,,,,,S,K,PERSON,,J,D,,,,,,,GOLD,bar,60000,AED,SOLD",
                "REF-2,2026-05-27,,,,,S,K,PERSON,,J,R,,,,,,,GOLD,coin,55000,AED,SOLD"
        ), tenantId, actor))
                .isInstanceOf(ImportRejectedException.class)
                .hasMessageContaining("maximum is 1");
        verify(reportService, never()).create(any(), any(), any());
    }
}
