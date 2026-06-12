package com.vyttah.goaml.model.dto.report;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;

import java.io.IOException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;

/**
 * Field-localized lenient {@link OffsetDateTime} deserializer for the curated {@link DpmsrCreateRequest} date
 * fields (B3). A server-side client (the AML cockpit's filing feed) sends some date fields as a bare
 * {@code yyyy-MM-dd} {@code LocalDate} (e.g. goods {@code registrationDate}), but the goAML contract types
 * those as {@code OffsetDateTime}, which the default Jackson deserializer rejects with a 400.
 *
 * <p>This accepts <b>both</b>:
 * <ul>
 *   <li>a bare ISO date {@code "2026-06-10"} → that date at UTC midnight ({@code 2026-06-10T00:00:00Z}); and</li>
 *   <li>a full ISO offset date-time {@code "2026-06-10T09:30:00Z"} (the existing wire shape).</li>
 * </ul>
 *
 * <p><b>Scope:</b> applied only via {@code @JsonDeserialize(using = ...)} on the specific curated request date
 * fields — it does <b>not</b> change the global Jackson configuration, so the full-fidelity
 * {@code DpmsrReportPayload} and every other {@code OffsetDateTime} in the app keep strict ISO binding.
 */
public class LenientOffsetDateTimeDeserializer extends JsonDeserializer<OffsetDateTime> {

    @Override
    public OffsetDateTime deserialize(JsonParser p, DeserializationContext ctx) throws IOException {
        String text = p.getValueAsString();
        if (text == null || text.isBlank()) {
            return null;
        }
        String trimmed = text.trim();
        try {
            // A bare date carries no 'T' time separator; a full date-time always does.
            if (trimmed.indexOf('T') < 0) {
                return LocalDate.parse(trimmed).atStartOfDay().atOffset(ZoneOffset.UTC);
            }
            return OffsetDateTime.parse(trimmed);
        } catch (DateTimeParseException e) {
            throw new IOException(
                    "Invalid date '" + trimmed + "': expected an ISO date (yyyy-MM-dd) or ISO date-time", e);
        }
    }
}
