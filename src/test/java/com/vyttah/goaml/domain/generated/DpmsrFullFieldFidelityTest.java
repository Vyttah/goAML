package com.vyttah.goaml.domain.generated;

import com.vyttah.goaml.engine.marshal.ReportMarshaller;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Step 0 of the full-schema-fidelity plan: <em>prove the loss is in the curated DTO, not the domain</em>.
 *
 * <p>A third real DPMSR (anonymized to {@code samples/USG-dpmsr-activity.xml}) carries ~13 fields our
 * {@code DpmsrCreateRequest} contract silently drops — goods {@code disposed_value}/{@code status_comments}/
 * {@code registration_number}/{@code identification_number}, entity {@code addresses}/{@code incorporation_date},
 * director {@code ssn}/{@code identification}/{@code addresses}/{@code employer_*}, reporting-person
 * {@code ssn}/{@code passport_*}. This test unmarshals the real XML into the generated model, re-marshals it,
 * re-reads it, and asserts <em>every one of those fields survives</em> — confirming the xjc-generated domain
 * is already 1:1 with the official XSD, so closing the gap is purely a contract-layer change.
 */
class DpmsrFullFieldFidelityTest {

    private final ReportMarshaller marshaller = new ReportMarshaller();

    @Test
    void everyDpmsrFieldSurvivesAMarshalRoundTripThroughTheDomain() throws IOException {
        Report original = marshaller.unmarshal(readResource("samples/USG-dpmsr-activity.xml"));

        // round-trip: domain -> XML -> domain (proves writing then reading loses nothing)
        Report report = marshaller.unmarshal(marshaller.marshal(original));

        // ---- header / reporting person ----
        assertThat(report.getRentityId()).isEqualTo(99999);
        assertThat(report.getReportCode()).isEqualTo(ReportType.DPMSR);
        TPersonRegistrationInReport rp = report.getReportingPerson();
        assertThat(rp.getSsn()).as("reporting_person ssn").isEqualTo("784199000000001");
        assertThat(rp.getPassportNumber()).as("reporting_person passport_number").isEqualTo("S0000001");
        assertThat(rp.getPassportCountry()).as("reporting_person passport_country").isEqualTo("IN");
        assertThat(rp.getPhones().getPhone().get(0).getTphNumber()).isEqualTo("0500000001");
        assertThat(rp.getAddresses().getAddress().get(0).getAddress()).contains("SAMPLE TOWER");

        ActivityType activity = report.getReportActivity();

        // ---- entity party ----
        TEntity entity = activity.getReportParties().getReportParty().get(0).getEntity();
        assertThat(entity.getName()).isEqualTo("SAMPLE JEWELLERY L.L.C");
        assertThat(entity.getIncorporationNumber()).isEqualTo("1000001");
        assertThat(entity.getIncorporationDate()).as("entity incorporation_date").isNotNull();
        assertThat(entity.getAddresses().getAddress().get(0).getAddress())
                .as("entity addresses").contains("SAMPLE BUILDING");

        // ---- director (entity_person, extends TPerson) ----
        TEntity.DirectorId director = entity.getDirectorId().get(0);
        assertThat(director.getRole()).isEqualTo(EntityPersonRoleType.PRTNR);
        assertThat(director.getSsn()).as("director ssn").isEqualTo("784198000000001");
        assertThat(director.getIdNumber()).as("director id_number").isEqualTo("784198000000001");
        assertThat(director.getAddresses().getAddress().get(0).getAddress())
                .as("director addresses").isEqualTo("AL RAS DUBAI");
        assertThat(director.getEmployerAddressId()).as("director employer_address_id").isNotNull();
        assertThat(director.getEmployerPhoneId()).as("director employer_phone_id").isNotNull();
        // real files use the inline <identification> form (the schema's choice), not the <identifications> wrapper
        TPersonIdentification id = director.getIdentification().get(0);
        assertThat(id.getType()).as("director identification type").isEqualTo("EID");
        assertThat(id.getNumber()).isEqualTo("784198000000001");
        assertThat(id.getIssueDate()).as("identification issue_date").isNotNull();
        assertThat(id.getExpiryDate()).as("identification expiry_date").isNotNull();
        assertThat(id.getIssueCountry()).isEqualTo("AE");

        // ---- goods / item ----
        TTransItem item = activity.getGoodsServices().getItem().get(0);
        assertThat(item.getItemType()).isEqualTo("GOLD");
        assertThat(item.getCurrencyCode()).isEqualTo(CurrencyType.AED);
        assertThat(item.getEstimatedValue()).isEqualByComparingTo(new BigDecimal("10000000.00"));
        assertThat(item.getDisposedValue()).as("goods disposed_value")
                .isEqualByComparingTo(new BigDecimal("10050000.00"));
        assertThat(item.getStatusComments()).as("goods status_comments").contains("CASH RECEIVED");
        assertThat(item.getRegistrationNumber()).as("goods registration_number").isEqualTo("SAMPLE0000001");
        assertThat(item.getIdentificationNumber()).as("goods identification_number").isEqualTo("REC0000000001");
    }

    private static byte[] readResource(String path) throws IOException {
        try (InputStream in = Thread.currentThread().getContextClassLoader().getResourceAsStream(path)) {
            assertThat(in).as("test resource %s present on classpath", path).isNotNull();
            return in.readAllBytes();
        }
    }
}
