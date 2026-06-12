package com.vyttah.goaml.model.dto.report;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * B3 — the curated {@link DpmsrCreateRequest} date fields accept BOTH a bare {@code yyyy-MM-dd} (a server-side
 * client such as the AML cockpit sends {@code registrationDate} as a {@code LocalDate}) AND a full ISO
 * offset date-time, without loosening the global Jackson config. The full-fidelity {@link DpmsrReportPayload}
 * is unaffected (it is not annotated).
 */
class LenientOffsetDateTimeDeserializerTest {

    private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Test
    void goodsRegistrationDateBindsFromABareDate() throws Exception {
        String json = """
                {"itemType":"GOLD","estimatedValue":90000,"registrationDate":"2026-06-10"}
                """;
        DpmsrCreateRequest.Goods goods = mapper.readValue(json, DpmsrCreateRequest.Goods.class);

        assertThat(goods.registrationDate())
                .isEqualTo(OffsetDateTime.of(2026, 6, 10, 0, 0, 0, 0, ZoneOffset.UTC));
    }

    @Test
    void goodsRegistrationDateStillBindsFromFullIsoDateTime() throws Exception {
        String json = """
                {"itemType":"GOLD","estimatedValue":90000,"registrationDate":"2026-06-10T09:30:00Z"}
                """;
        DpmsrCreateRequest.Goods goods = mapper.readValue(json, DpmsrCreateRequest.Goods.class);

        assertThat(goods.registrationDate())
                .isEqualTo(OffsetDateTime.of(2026, 6, 10, 9, 30, 0, 0, ZoneOffset.UTC));
    }

    @Test
    void siblingPersonBirthdateAndSubmissionDateAlsoAcceptABareDate() throws Exception {
        // a sibling nested date field (person.birthdate)
        String personJson = """
                {"firstName":"Sara","lastName":"Khan","birthdate":"1990-01-02"}
                """;
        DpmsrCreateRequest.Person person = mapper.readValue(personJson, DpmsrCreateRequest.Person.class);
        assertThat(person.birthdate())
                .isEqualTo(OffsetDateTime.of(1990, 1, 2, 0, 0, 0, 0, ZoneOffset.UTC));

        // identification dates
        String idJson = """
                {"type":"EID","number":"784","issueDate":"2020-05-01","expiryDate":"2030-05-01"}
                """;
        DpmsrCreateRequest.Identification id =
                mapper.readValue(idJson, DpmsrCreateRequest.Identification.class);
        assertThat(id.issueDate()).isEqualTo(OffsetDateTime.of(2020, 5, 1, 0, 0, 0, 0, ZoneOffset.UTC));
        assertThat(id.expiryDate()).isEqualTo(OffsetDateTime.of(2030, 5, 1, 0, 0, 0, 0, ZoneOffset.UTC));
    }

    @Test
    void topLevelSubmissionDateBindsFromABareDateAndFullIso() throws Exception {
        String bare = """
                {"entityReference":"R-1","submissionDate":"2026-06-12","parties":[],"goods":[]}
                """;
        DpmsrCreateRequest reqBare = mapper.readValue(bare, DpmsrCreateRequest.class);
        assertThat(reqBare.submissionDate())
                .isEqualTo(OffsetDateTime.of(2026, 6, 12, 0, 0, 0, 0, ZoneOffset.UTC));

        String full = """
                {"entityReference":"R-2","submissionDate":"2026-06-12T08:00:00Z","parties":[],"goods":[]}
                """;
        DpmsrCreateRequest reqFull = mapper.readValue(full, DpmsrCreateRequest.class);
        assertThat(reqFull.submissionDate())
                .isEqualTo(OffsetDateTime.of(2026, 6, 12, 8, 0, 0, 0, ZoneOffset.UTC));
    }
}
