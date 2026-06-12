package com.vyttah.goaml.model.mapper.report;

import com.vyttah.goaml.engine.build.DpmsrReportInput;
import com.vyttah.goaml.engine.validation.Severity;
import com.vyttah.goaml.engine.validation.ValidationMessage;
import com.vyttah.goaml.model.dto.report.DpmsrCreateRequest;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DpmsrRequestMapperTest {

    private final DpmsrRequestMapper mapper = new DpmsrRequestMapper();

    @Test
    void mapsEntityPartyWithDirectorAndGoods() {
        DpmsrCreateRequest.Director dir = new DpmsrCreateRequest.Director(
                "M", "Ali", "Abdulla", odt("1990-01-01T12:00:00"), "Z0000000", "IN",
                "Z0000000", "IN", "AE", "ATR",
                new DpmsrCreateRequest.Phone("BU", "L", "971", "500000000"));
        DpmsrCreateRequest.Entity entity = new DpmsrCreateRequest.Entity(
                "Example Jewellery LLC", "S E C", "100000", "DUBAI", "AE",
                new DpmsrCreateRequest.Phone("BU", "L", "971", "500000000"), List.of(dir));
        DpmsrCreateRequest.Party party = new DpmsrCreateRequest.Party("Seller", "comment", entity, null);
        DpmsrCreateRequest.Goods gold = new DpmsrCreateRequest.Goods(
                "GOLD", "Emirates Gold", "1kg bar", "Buyer", "SOLD",
                new BigDecimal("75000.00"), "AED", new BigDecimal("1000"), "GRAM", null,
                new BigDecimal("76000.00"), "Cash received", "INV-001", "REC-001");
        DpmsrCreateRequest.Person mlro = new DpmsrCreateRequest.Person(
                "F", "Sara", "Khan", null, "AE", "AE", "AE", "784199012345678", null, "MLRO",
                new DpmsrCreateRequest.Phone("BU", "L", "971", "44441234"),
                new DpmsrCreateRequest.Address("BU", "Office 1", "Dubai", "AE", "Dubai"), null);

        DpmsrReportInput input = mapper.toInput(new DpmsrCreateRequest(
                "DXB", "PAY-1", odt("2026-06-02T12:00:00"), null, "r", "a",
                List.of("DPMSJ"), mlro, null, List.of(party), List.of(gold)), 3177);

        assertThat(input.rentityId()).isEqualTo(3177);
        assertThat(input.parties()).hasSize(1);
        assertThat(input.parties().get(0).getEntity()).isNotNull();
        assertThat(input.parties().get(0).getEntity().getDirectorId()).hasSize(1);
        assertThat(input.goods()).hasSize(1);
        assertThat(input.goods().get(0).getItemType()).isEqualTo("GOLD");
        assertThat(input.goods().get(0).getDisposedValue()).isEqualByComparingTo(new BigDecimal("76000.00"));
        assertThat(input.goods().get(0).getStatusComments()).isEqualTo("Cash received");
        assertThat(input.goods().get(0).getRegistrationNumber()).isEqualTo("INV-001");
        assertThat(input.goods().get(0).getIdentificationNumber()).isEqualTo("REC-001");
        assertThat(input.reportingPerson().getFirstName()).isEqualTo("Sara");
    }

    @Test
    void mapsPersonPartyAsLenientPerson() {
        // B5: person parties map to the lenient t_person (getPerson), NOT t_person_my_client, so a minimal
        // feed can be VALID while a rich one still carries every field. taxRegNumber maps but isn't mandatory.
        DpmsrCreateRequest.Identification eid = new DpmsrCreateRequest.Identification(
                "EID", "784198012345678", odt("2020-01-15T00:00:00"), odt("2030-01-14T00:00:00"), "AE");
        DpmsrCreateRequest.Person buyer = new DpmsrCreateRequest.Person(
                "M", "Mohamad", "Al-Jaber", odt("1985-03-12T00:00:00"), "AE", "AE", "AE",
                "784198012345678", "TRN-9", null,
                new DpmsrCreateRequest.Phone("PRIVT", "L", "971", "501112233"),
                new DpmsrCreateRequest.Address("BU", "Villa 9", "Dubai", "AE", "Dubai"), List.of(eid));
        DpmsrCreateRequest.Party party = new DpmsrCreateRequest.Party("Walk-in buyer", null, null, buyer);

        DpmsrReportInput input = mapper.toInput(req(party), 3177);

        var person = input.parties().get(0).getPerson();
        assertThat(person).as("person party maps to lenient t_person").isNotNull();
        assertThat(input.parties().get(0).getPersonMyClient()).as("not my_client").isNull();
        assertThat(input.parties().get(0).getEntity()).isNull();
        assertThat(person.getIdentifications().getIdentification()).hasSize(1);
        assertThat(person.getTaxRegNumber()).isEqualTo("TRN-9");
        // B17: a party person address is now mapped (was previously dropped)
        assertThat(person.getAddresses().getAddress()).hasSize(1);
        assertThat(person.getAddresses().getAddress().get(0).getCity()).isEqualTo("Dubai");
    }

    @Test
    void minimalPersonPartyOmitsOptionalBlocksAndStaysLenient() {
        // B5: only first/last name supplied — no phones/addresses/identifications wrappers are emitted, so the
        // person is a clean minimal t_person (the CSV-importer shape) rather than a my_client with mandatory holes.
        DpmsrCreateRequest.Person buyer = new DpmsrCreateRequest.Person(
                null, "Lin", "Chen", null, null, null, null, null, null, null, null, null, null);
        DpmsrCreateRequest.Party party = new DpmsrCreateRequest.Party("Walk-in buyer", null, null, buyer);

        DpmsrReportInput input = mapper.toInput(req(party), 3177);

        var person = input.parties().get(0).getPerson();
        assertThat(person).isNotNull();
        assertThat(person.getPhones()).as("no empty phones wrapper").isNull();
        assertThat(person.getAddresses()).isNull();
        assertThat(person.getIdentifications()).isNull();
        assertThat(person.getTaxRegNumber()).isNull();
    }

    @Test
    void partyWithNeitherEntityNorPersonThrows() {
        DpmsrCreateRequest.Party empty = new DpmsrCreateRequest.Party("x", null, null, null);
        assertThatThrownBy(() -> mapper.toInput(req(empty), 3177))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void partyWithBothEntityAndPersonIsRejectedNotSilentlyHalved() {
        // A party carrying both subjects is ambiguous — refusing beats silently dropping the person.
        DpmsrCreateRequest.Entity entity = new DpmsrCreateRequest.Entity(
                "Both Set LLC", null, null, null, null, null, null);
        DpmsrCreateRequest.Person person = new DpmsrCreateRequest.Person(
                null, "Also", "Here", null, null, null, null, null, null, null, null, null, null);
        DpmsrCreateRequest.Party both = new DpmsrCreateRequest.Party("x", null, entity, person);

        assertThatThrownBy(() -> mapper.toInput(req(both), 3177))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("exactly one of person|entity");
    }

    @Test
    void normalizationThatChangesANameSurfacesAWarning() {
        DpmsrCreateRequest.Entity entity = new DpmsrCreateRequest.Entity(
                "GOLD & DIAMONDS (DUBAI) LLC", null, null, null, null, null, null);
        DpmsrCreateRequest.Party party = new DpmsrCreateRequest.Party("Seller", null, entity, null);
        List<ValidationMessage> messages = new ArrayList<>();

        DpmsrReportInput input = mapper.toInput(req(party), 3177, messages);

        assertThat(input.parties().get(0).getEntity().getName()).isEqualTo("GOLD and DIAMONDS DUBAI LLC");
        assertThat(messages)
                .anySatisfy(m -> {
                    assertThat(m.severity()).isEqualTo(Severity.WARNING);
                    assertThat(m.code()).isEqualTo(DpmsrRequestMapper.NAME_NORMALIZED);
                    assertThat(m.path()).isEqualTo("parties[0].entity.name");
                    assertThat(m.message()).contains("GOLD & DIAMONDS (DUBAI) LLC")
                            .contains("GOLD and DIAMONDS DUBAI LLC");
                });
    }

    @Test
    void unchangedNamesEmitNoMessages() {
        DpmsrCreateRequest.Person buyer = new DpmsrCreateRequest.Person(
                null, "Lin", "Chen", null, null, null, null, null, null, null, null, null, null);
        DpmsrCreateRequest.Party party = new DpmsrCreateRequest.Party("Buyer", null, null, buyer);
        List<ValidationMessage> messages = new ArrayList<>();

        mapper.toInput(req(party), 3177, messages);

        assertThat(messages).isEmpty();
    }

    @Test
    void normalizationThatEmptiesANameIsAClearError() {
        // "()" normalizes away entirely — that must be a clear NAME_UNREPRESENTABLE error, not a raw
        // XSD SAX failure downstream.
        DpmsrCreateRequest.Entity entity = new DpmsrCreateRequest.Entity(
                "()", null, null, null, null, null, null);
        DpmsrCreateRequest.Party party = new DpmsrCreateRequest.Party("Seller", null, entity, null);
        List<ValidationMessage> messages = new ArrayList<>();

        mapper.toInput(req(party), 3177, messages);

        assertThat(messages)
                .anySatisfy(m -> {
                    assertThat(m.severity()).isEqualTo(Severity.ERROR);
                    assertThat(m.code()).isEqualTo(DpmsrRequestMapper.NAME_UNREPRESENTABLE);
                    assertThat(m.path()).isEqualTo("parties[0].entity.name");
                });
    }

    @Test
    void reportingPersonMapsTaxRegNumberAndAlias() {
        // Finding 13: t_person_registration_in_report HAS slots for tax_reg_number and alias — map them.
        DpmsrCreateRequest.Person mlro = new DpmsrCreateRequest.Person(
                "F", "Sara", "Khan", null, null, null, null, null, "TRN-MLRO", "MLRO",
                null, null, null, null, "SK");
        DpmsrCreateRequest.Person buyer = new DpmsrCreateRequest.Person(
                null, "Lin", "Chen", null, null, null, null, null, null, null, null, null, null);
        DpmsrCreateRequest.Goods gold = new DpmsrCreateRequest.Goods(
                "GOLD", null, null, null, null, new BigDecimal("90000.00"), "AED", null, null, null,
                null, null, null, null);
        DpmsrCreateRequest request = new DpmsrCreateRequest(null, "PAY-RP", odt("2026-06-02T12:00:00"),
                null, "r", "a", List.of("DPMSJ"), mlro, null,
                List.of(new DpmsrCreateRequest.Party("Buyer", null, null, buyer)), List.of(gold));

        DpmsrReportInput input = mapper.toInput(request, 3177);

        assertThat(input.reportingPerson().getTaxRegNumber()).isEqualTo("TRN-MLRO");
        assertThat(input.reportingPerson().getAlias()).isEqualTo("SK");
    }

    private static DpmsrCreateRequest req(DpmsrCreateRequest.Party party) {
        DpmsrCreateRequest.Person mlro = new DpmsrCreateRequest.Person(
                null, "Sara", "Khan", null, null, null, null, null, null, null, null, null, null);
        DpmsrCreateRequest.Goods gold = new DpmsrCreateRequest.Goods(
                "GOLD", null, null, null, null, new BigDecimal("90000.00"), "AED", null, null, null,
                null, null, null, null);
        return new DpmsrCreateRequest(null, "PAY-X", odt("2026-06-02T12:00:00"), null, "r", "a",
                List.of("DPMSJ"), mlro, null, List.of(party), List.of(gold));
    }

    private static OffsetDateTime odt(String isoLocal) {
        return OffsetDateTime.parse(isoLocal + "Z").withOffsetSameInstant(ZoneOffset.UTC);
    }
}
