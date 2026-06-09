package com.vyttah.goaml.model.dto.report;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.vyttah.goaml.domain.generated.ActivityType;
import com.vyttah.goaml.domain.generated.CurrencyType;
import com.vyttah.goaml.domain.generated.EntityPersonRoleType;
import com.vyttah.goaml.domain.generated.Report;
import com.vyttah.goaml.domain.generated.TEntity;
import com.vyttah.goaml.domain.generated.TTransItem;
import com.vyttah.goaml.domain.jackson.GeneratedEnumJacksonModule;
import com.vyttah.goaml.engine.build.ActivityReportBuilder;
import com.vyttah.goaml.engine.build.DpmsrReportBuilder;
import com.vyttah.goaml.engine.build.DpmsrReportInput;
import com.vyttah.goaml.engine.marshal.ReportMarshaller;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Step 1 of the full-schema-fidelity plan: prove the new {@link DpmsrReportPayload} contract carries the full
 * schema losslessly through JSON. We build a payload from the real (anonymized) sample's generated objects,
 * serialize + deserialize it with a production-equivalent {@code ObjectMapper} (JavaTime + the
 * {@link GeneratedEnumJacksonModule}), map it to the engine input, build the {@link Report}, and assert that
 * the previously-dropped fields are all present — both in the typed input and in the marshalled XML.
 */
class DpmsrReportPayloadTest {

    private final ReportMarshaller marshaller = new ReportMarshaller();

    // mirrors Spring Boot's auto-configured ObjectMapper: JavaTime + ISO dates + our generated-enum module
    private final JsonMapper mapper = JsonMapper.builder()
            .addModule(new JavaTimeModule())
            .addModule(new GeneratedEnumJacksonModule())
            .serializationInclusion(JsonInclude.Include.NON_NULL)
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .build();

    @Test
    void payloadRoundTripsThroughJsonAndCarriesEveryFieldIntoTheBuiltReport() throws IOException {
        Report sample = marshaller.unmarshal(readResource("samples/USG-dpmsr-activity.xml"));
        ActivityType activity = sample.getReportActivity();

        DpmsrReportPayload payload = new DpmsrReportPayload(
                sample.getRentityBranch(),
                sample.getEntityReference(),
                sample.getSubmissionDate(),
                sample.getFiuRefNumber(),
                sample.getReportingPerson(),
                sample.getLocation(),
                sample.getReason(),
                sample.getAction(),
                java.util.List.of("DPMSJ"),
                activity.getReportParties().getReportParty(),
                activity.getGoodsServices().getItem());

        // contract -> JSON -> contract (proves the generated types bind both ways)
        DpmsrReportPayload back = mapper.readValue(mapper.writeValueAsString(payload), DpmsrReportPayload.class);

        // contract -> engine input (server injects rentity_id)
        DpmsrReportInput input = back.toInput(99999);
        assertThat(input.rentityId()).isEqualTo(99999);
        assertThat(input.reportingPerson().getSsn()).isEqualTo("784199000000001");
        assertThat(input.reportingPerson().getPassportNumber()).isEqualTo("S0000001");

        TEntity entity = input.parties().get(0).getEntity();
        assertThat(entity.getIncorporationDate()).isNotNull();
        assertThat(entity.getAddresses().getAddress().get(0).getAddress()).contains("SAMPLE BUILDING");
        TEntity.DirectorId director = entity.getDirectorId().get(0);
        assertThat(director.getRole()).isEqualTo(EntityPersonRoleType.PRTNR);
        assertThat(director.getSsn()).isEqualTo("784198000000001");
        assertThat(director.getIdentification().get(0).getType()).isEqualTo("EID");

        TTransItem item = input.goods().get(0);
        assertThat(item.getCurrencyCode()).isEqualTo(CurrencyType.AED);
        assertThat(item.getDisposedValue()).isEqualByComparingTo(new BigDecimal("10050000.00"));
        assertThat(item.getRegistrationNumber()).isEqualTo("SAMPLE0000001");
        assertThat(item.getIdentificationNumber()).isEqualTo("REC0000000001");

        // engine input -> built Report -> XML: the fields reach the wire (build() only needs the activity builder)
        Report built = new DpmsrReportBuilder(new ActivityReportBuilder(), null, null, null).build(input);
        String xml = new String(marshaller.marshal(built), StandardCharsets.UTF_8);
        assertThat(xml)
                .contains("<disposed_value>10050000.00</disposed_value>")
                .contains("<registration_number>SAMPLE0000001</registration_number>")
                .contains("<identification_number>REC0000000001</identification_number>")
                .contains("<status_comments>CASH RECEIVED AGAINST 95KG GOLD SOLD</status_comments>")
                .contains("<ssn>784199000000001</ssn>")
                .contains("<passport_number>S0000001</passport_number>")
                .contains("<incorporation_date>2023-06-05")
                .contains("<role>PRTNR</role>");
    }

    private static byte[] readResource(String path) throws IOException {
        try (InputStream in = Thread.currentThread().getContextClassLoader().getResourceAsStream(path)) {
            assertThat(in).as("test resource %s present on classpath", path).isNotNull();
            return in.readAllBytes();
        }
    }
}
