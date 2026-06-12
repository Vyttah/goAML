package com.vyttah.goaml.model.mapper.report;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.vyttah.goaml.domain.jackson.GeneratedEnumJacksonModule;
import com.vyttah.goaml.engine.build.ActivityReportBuilder;
import com.vyttah.goaml.engine.build.DpmsrReportBuilder;
import com.vyttah.goaml.engine.build.DpmsrReportInput;
import com.vyttah.goaml.engine.build.ValidatedReport;
import com.vyttah.goaml.engine.jurisdiction.JurisdictionRegistry;
import com.vyttah.goaml.engine.lookup.LookupService;
import com.vyttah.goaml.engine.marshal.ReportMarshaller;
import com.vyttah.goaml.engine.validation.ReportValidator;
import com.vyttah.goaml.engine.validation.XsdSchemaValidator;
import com.vyttah.goaml.model.dto.report.DpmsrCreateRequest;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * B13 — wire-key drift safety net. goAML's ObjectMapper ignores unknown JSON keys, so a rename on either side
 * silently drops a field from the filed XML with no error. This test populates a MAXIMAL curated
 * {@link DpmsrCreateRequest} (entity + director + person party + goods + address + phone + identification),
 * round-trips it through JSON (proving the wire keys bind), runs the real engine, and asserts every populated
 * field re-appears in the marshalled XML. A future key rename breaks an assertion here instead of silently
 * shipping an incomplete FIU report.
 *
 * <p>Synthetic non-PII data only. NB: the curated DTO models person + entity parties (not {@code account}),
 * so the account subject's wire fidelity is covered by the full-fidelity {@code DpmsrReportPayload} path
 * ({@code DpmsrFullFieldFidelityTest} / {@code ReportApiE2ETest}).
 */
class DpmsrCuratedWireFidelityTest {

    // Matches the Spring Boot ObjectMapper: ISO date strings (not epoch numbers), generated-enum binding.
    private final ObjectMapper mapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .registerModule(new GeneratedEnumJacksonModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private final DpmsrRequestMapper requestMapper = new DpmsrRequestMapper();

    private final DpmsrReportBuilder builder = new DpmsrReportBuilder(
            new ActivityReportBuilder(),
            new ReportValidator(new JurisdictionRegistry(), new LookupService()),
            new XsdSchemaValidator(),
            new ReportMarshaller());

    @Test
    void aMaximalCuratedRequestRoundTripsEveryFieldIntoTheValidXml() throws Exception {
        DpmsrCreateRequest original = maximalRequest();

        // round-trip through JSON: proves the wire keys deserialize back to the same populated record
        String json = mapper.writeValueAsString(original);
        DpmsrCreateRequest bound = mapper.readValue(json, DpmsrCreateRequest.class);

        DpmsrReportInput input = requestMapper.toInput(bound, 3177);
        ValidatedReport vr = builder.buildAndValidate(input, "ae");

        assertThat(vr.isValid()).as("errors=%s", vr.xsd().errors()).isTrue();
        String xml = vr.xml();

        // reporting person — including the previously-dropped tax_reg_number + alias slots (finding 13)
        assertThat(xml).contains("<first_name>Sara</first_name>").contains("<last_name>Khan</last_name>")
                .contains("<tax_reg_number>TRN-MLRO</tax_reg_number>")
                .contains("<alias>SK</alias>")
                .contains("<occupation>MLRO</occupation>");

        // entity + its carry-through fields (B4) + director
        assertThat(xml)
                .contains("Synthetic Bullion FZE")              // entity name
                .contains("<commercial_name>Synth Bullion</commercial_name>")
                .contains("<incorporation_number>INC-1</incorporation_number>")
                .contains("<incorporation_state>Dubai</incorporation_state>")
                .contains("<incorporation_country_code>AE</incorporation_country_code>")
                .contains("<incorporation_date>")
                .contains("<tax_reg_number>Y</tax_reg_number>")
                .contains("<business>Precious metals trading</business>")
                // entity address + phone
                .contains("Trade Centre Tower")
                .contains("<tph_number>44440000</tph_number>")
                // director
                .contains("<first_name>Ravi</first_name>")
                .contains("<last_name>Patel</last_name>");

        // person party (lenient t_person) — name, ids, address, phone, identification, email, alias,
        // plus the previously-unasserted facts: country_of_birth (≠ nationality), residence, occupation
        assertThat(xml)
                .contains("<first_name>John</first_name>")
                .contains("<last_name>Doe</last_name>")
                .contains("<id_number>784-1990-1234567-1</id_number>")
                .contains("<alias>JD</alias>")
                .contains("john.doe@example.test")
                .contains("<country_of_birth>PK</country_of_birth>")
                .contains("<nationality1>IN</nationality1>")
                .contains("<residence>AE</residence>")
                .contains("<occupation>Trader</occupation>")
                .contains("Marina Walk")                          // person address line
                .contains("<tph_number>501112233</tph_number>")
                // identification block
                .contains("<type>PASSP</type>")
                .contains("<number>P1234567</number>");

        // a +04:00 (UAE) local-midnight birthdate must file as the SAME calendar date — goAML dateTime has
        // no zone, so a UTC conversion would file 1990-03-11T20:00:00 (the previous day)
        assertThat(xml).contains("<birthdate>1990-03-12T00:00:00</birthdate>");

        // goods — every optional field, including the previously-unasserted numerics/dates
        assertThat(xml)
                .contains("<item_type>GOLD</item_type>")
                .contains("<item_make>Emirates Gold</item_make>")
                .contains("1kg cast bar")                         // description
                .contains("<status_code>SOLD</status_code>")
                .contains("<estimated_value>95000.00</estimated_value>")
                .contains("<size>1000</size>")
                .contains("<disposed_value>96000.00</disposed_value>")
                .contains("<registration_date>2026-06-01T00:00:00</registration_date>")
                .contains("<registration_number>REG-9</registration_number>")
                .contains("<identification_number>ID-9</identification_number>")
                .contains("<status_comments>Cash received in full</status_comments>");
    }

    private static DpmsrCreateRequest maximalRequest() {
        DpmsrCreateRequest.Phone entityPhone = new DpmsrCreateRequest.Phone("BU", "L", "971", "44440000");
        DpmsrCreateRequest.Phone personPhone = new DpmsrCreateRequest.Phone("PRIVT", "M", "971", "501112233");
        DpmsrCreateRequest.Address entityAddr = new DpmsrCreateRequest.Address("BU", "Trade Centre Tower", "Dubai", "AE", "Dubai");
        DpmsrCreateRequest.Address personAddr = new DpmsrCreateRequest.Address("RES", "Marina Walk", "Dubai", "AE", "Dubai");

        DpmsrCreateRequest.Director director = new DpmsrCreateRequest.Director(
                "M", "Ravi", "Patel", odt("1970-05-05T00:00:00"), "P0000001", "IN",
                "784-1970-7654321-1", "IN", "AE", "PRTNR", entityPhone);

        // NB: t_entity tax_reg_number has a 1-char XSD maxLength (a known goAML quirk) — use a single char.
        DpmsrCreateRequest.Entity entity = new DpmsrCreateRequest.Entity(
                "Synthetic Bullion FZE", "Synth Bullion", "INC-1", "Dubai", "AE", entityPhone, List.of(director),
                odt("2010-01-01T00:00:00"), "Y", "Precious metals trading", entityAddr);

        DpmsrCreateRequest.Identification id = new DpmsrCreateRequest.Identification(
                "PASSP", "P1234567", odt("2020-01-01T00:00:00"), odt("2030-01-01T00:00:00"), "AE");
        // birthdate at UAE local midnight (+04:00) — pins that the calendar date survives into the XML;
        // country_of_birth (PK) deliberately differs from nationality (IN) so a swap/fabrication breaks here
        DpmsrCreateRequest.Person personParty = new DpmsrCreateRequest.Person(
                "M", "John", "Doe", OffsetDateTime.parse("1990-03-12T00:00:00+04:00"), "PK", "IN", "AE",
                "784-1990-1234567-1", "TRN-P", "Trader", personPhone, personAddr, List.of(id),
                "john.doe@example.test", "JD");

        DpmsrCreateRequest.Party entityParty = new DpmsrCreateRequest.Party("Seller", "VIP", entity, null);
        DpmsrCreateRequest.Party personPartyWrap = new DpmsrCreateRequest.Party("Buyer", "Walk-in", null, personParty);

        DpmsrCreateRequest.Goods goods = new DpmsrCreateRequest.Goods(
                "GOLD", "Emirates Gold", "1kg cast bar", "Buyer", "SOLD",
                new BigDecimal("95000.00"), "AED", new BigDecimal("1000"), "GRAM",
                odt("2026-06-01T00:00:00"), new BigDecimal("96000.00"), "Cash received in full", "REG-9", "ID-9");

        DpmsrCreateRequest.Person mlro = new DpmsrCreateRequest.Person(
                "F", "Sara", "Khan", null, null, null, null, null, "TRN-MLRO", "MLRO", null, null, null,
                null, "SK");

        return new DpmsrCreateRequest("DXB", "B13-MAX", odt("2026-06-02T12:00:00"), null, "DPMS threshold met",
                "Filed", List.of("DPMSJ", "DPMSI"), mlro, null, List.of(entityParty, personPartyWrap),
                List.of(goods));
    }

    private static OffsetDateTime odt(String isoLocal) {
        return OffsetDateTime.parse(isoLocal + "Z").withOffsetSameInstant(ZoneOffset.UTC);
    }
}
