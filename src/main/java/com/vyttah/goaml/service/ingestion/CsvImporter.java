package com.vyttah.goaml.service.ingestion;

import com.vyttah.goaml.config.ingestion.IngestionProperties;
import com.vyttah.goaml.model.dto.report.DpmsrCreateRequest;
import com.vyttah.goaml.model.dto.report.DpmsrCreateRequest.Entity;
import com.vyttah.goaml.model.dto.report.DpmsrCreateRequest.Goods;
import com.vyttah.goaml.model.dto.report.DpmsrCreateRequest.Party;
import com.vyttah.goaml.model.dto.report.DpmsrCreateRequest.Person;
import com.vyttah.goaml.service.ingestion.IngestionExceptions.ImportRejectedException;
import com.vyttah.goaml.service.report.ReportExceptions;
import com.vyttah.goaml.service.report.ReportResult;
import com.vyttah.goaml.service.report.ReportService;
import lombok.RequiredArgsConstructor;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Imports a flat DPMSR CSV (Phase 11.3): one row = one report. Each row maps to a {@link DpmsrCreateRequest}
 * and goes through the existing {@link ReportService#create} (build + validate + persist + idempotency), so
 * there is no parallel persistence path. Per-row failures (bad value, missing required cell, validation,
 * duplicate) are isolated — that row becomes a {@code FAILED} {@link ImportRowResult} and the rest proceed.
 * Whole-file problems (unreadable, missing required headers, over the row cap) throw
 * {@link ImportRejectedException} before any report is created.
 *
 * <p><b>Template (v1, pending sign-off):</b> a single counterparty (PERSON or ENTITY) + one primary good —
 * see {@code .planning/plans/phase-11-ingestion.md} §3. Column access is header-name driven, so a sign-off
 * change to the layout is a mapping tweak, not a rewrite.
 */
@Component
@RequiredArgsConstructor
public class CsvImporter {

    // The columns a DPMSR row needs to stand a chance of validating. Beyond the obvious identity/goods/
    // reporting-person fields, two are required because the goAML schema mandates them and the flat template
    // is their only source: `indicators` (report_indicators is mandatory) and `party_reason` (report_party
    // must carry one of country/is_suspected/significance/reason — the template only offers reason). Without
    // these columns every row would fail XSD, so we reject the whole file up front rather than silently
    // produce all-INVALID rows. (Cells may still be blank → that row fails with a clear per-row message.)
    private static final Set<String> REQUIRED_HEADERS = Set.of(
            "entity_reference", "submission_date", "party_type", "party_reason", "indicators",
            "good_item_type", "good_estimated_value",
            "reporting_person_first_name", "reporting_person_last_name");

    private final ReportService reportService;
    private final IngestionProperties properties;

    public List<ImportRowResult> importCsv(byte[] csv, UUID tenantId, UUID actorUserId) {
        List<CSVRecord> records;
        Set<String> headers;
        CSVFormat format = CSVFormat.DEFAULT.builder()
                .setHeader().setSkipHeaderRecord(true).setTrim(true).setIgnoreEmptyLines(true).build();
        try (CSVParser parser = format.parse(
                new InputStreamReader(new ByteArrayInputStream(csv), StandardCharsets.UTF_8))) {
            headers = parser.getHeaderMap().keySet();
            records = parser.getRecords();
        } catch (IOException | IllegalArgumentException | IllegalStateException e) {
            throw new ImportRejectedException("Unreadable CSV: " + e.getMessage());
        }

        List<String> missing = REQUIRED_HEADERS.stream().filter(h -> !headers.contains(h)).sorted().toList();
        if (!missing.isEmpty()) {
            throw new ImportRejectedException("CSV is missing required columns: " + missing);
        }
        if (records.size() > properties.maxRows()) {
            throw new ImportRejectedException(
                    "CSV has " + records.size() + " rows; the maximum is " + properties.maxRows());
        }

        List<ImportRowResult> results = new ArrayList<>();
        int row = 0;
        for (CSVRecord rec : records) {
            row++;
            String ref = opt(rec, "entity_reference");
            try {
                ReportResult rr = reportService.create(toRequest(rec), tenantId, actorUserId);
                results.add(ImportRowResult.created(row, ref, rr.status(), rr.reportId(),
                        rr.validationMessages().stream()
                                .map(m -> "[" + m.code() + "] " + m.message() + " (" + m.path() + ")").toList()));
            } catch (ReportExceptions.DuplicateEntityReferenceException e) {
                results.add(ImportRowResult.failed(row, ref, "Duplicate entity_reference " + ref));
            } catch (RuntimeException e) {
                results.add(ImportRowResult.failed(row, ref, e.getMessage()));
            }
        }
        return results;
    }

    private DpmsrCreateRequest toRequest(CSVRecord rec) {
        Person reportingPerson = new Person(null, req(rec, "reporting_person_first_name"),
                req(rec, "reporting_person_last_name"), null, null, null, null, null, null, null, null, null, null);

        return new DpmsrCreateRequest(
                opt(rec, "rentity_branch"),
                req(rec, "entity_reference"),
                parseDateTime(req(rec, "submission_date"), "submission_date"),
                opt(rec, "fiu_ref_number"),
                opt(rec, "reason"),
                opt(rec, "action"),
                splitList(opt(rec, "indicators")),
                reportingPerson,
                null,                       // location not modelled in the flat template
                List.of(toParty(rec)),
                List.of(toGoods(rec)));
    }

    private Party toParty(CSVRecord rec) {
        String type = req(rec, "party_type").toUpperCase();
        return switch (type) {
            // country_of_birth comes ONLY from its own (optional) column — it is a distinct fact from
            // nationality and must never be fabricated from it (a Pakistani-born UAE national exists).
            case "PERSON" -> new Party(opt(rec, "party_reason"), null, null, new Person(
                    null, req(rec, "person_first_name"), req(rec, "person_last_name"),
                    parseNullableDate(opt(rec, "person_birthdate")),
                    opt(rec, "person_country_of_birth"), opt(rec, "person_nationality"),
                    null, opt(rec, "person_id_number"), null, null, null, null, null));
            case "ENTITY" -> new Party(opt(rec, "party_reason"), null, new Entity(
                    req(rec, "entity_name"), null, opt(rec, "entity_incorporation_number"),
                    null, opt(rec, "entity_incorporation_country"), null, null), null);
            default -> throw new IllegalArgumentException("party_type must be PERSON or ENTITY, was: " + type);
        };
    }

    private Goods toGoods(CSVRecord rec) {
        return new Goods(req(rec, "good_item_type"), null, opt(rec, "good_description"), null,
                opt(rec, "good_status_code"), parseMoney(req(rec, "good_estimated_value")),
                opt(rec, "good_currency_code"), null, null, null,
                null, null, opt(rec, "good_registration_number"), null);
    }

    /** Required cell — blank/absent throws (→ the row fails with a clear message). */
    private static String req(CSVRecord rec, String column) {
        String v = opt(rec, column);
        if (v == null) {
            throw new IllegalArgumentException("Missing required value: " + column);
        }
        return v;
    }

    /** Optional cell — returns null when absent or blank. */
    private static String opt(CSVRecord rec, String column) {
        if (!rec.isMapped(column) || !rec.isSet(column)) {
            return null;
        }
        String v = rec.get(column);
        return (v == null || v.isBlank()) ? null : v;
    }

    private static List<String> splitList(String value) {
        if (value == null) {
            return List.of();
        }
        return java.util.Arrays.stream(value.split(";")).map(String::trim).filter(s -> !s.isEmpty()).toList();
    }

    private static BigDecimal parseMoney(String value) {
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid numeric value '" + value + "' for good_estimated_value");
        }
    }

    private static OffsetDateTime parseNullableDate(String value) {
        return value == null ? null : parseDateTime(value, "date");
    }

    /** Lenient: accepts a full ISO offset/date-time, or a plain {@code yyyy-MM-dd} (→ start-of-day UTC). */
    private static OffsetDateTime parseDateTime(String value, String column) {
        try {
            if (value.length() > 10) {
                return OffsetDateTime.parse(value);
            }
            return LocalDate.parse(value).atStartOfDay().atOffset(ZoneOffset.UTC);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("Invalid date '" + value + "' for " + column);
        }
    }
}
