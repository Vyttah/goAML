package com.vyttah.goaml.model.mapper.report;

import com.vyttah.goaml.engine.build.DpmsrReportInput;
import com.vyttah.goaml.model.dto.report.DpmsrCreateRequest;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
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
                new BigDecimal("75000.00"), "AED", new BigDecimal("1000"), "GRAM", null);
        DpmsrCreateRequest.Person mlro = new DpmsrCreateRequest.Person(
                "F", "Sara", "Khan", null, "AE", "AE", "784199012345678", null, "MLRO",
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
        assertThat(input.reportingPerson().getFirstName()).isEqualTo("Sara");
    }

    @Test
    void mapsPersonPartyAsMyClient() {
        DpmsrCreateRequest.Identification eid = new DpmsrCreateRequest.Identification(
                "EID", "784198012345678", odt("2020-01-15T00:00:00"), odt("2030-01-14T00:00:00"), "AE");
        DpmsrCreateRequest.Person buyer = new DpmsrCreateRequest.Person(
                "M", "Mohamad", "Al-Jaber", odt("1985-03-12T00:00:00"), "AE", "AE",
                "784198012345678", "Y", null,
                new DpmsrCreateRequest.Phone("PRIVT", "L", "971", "501112233"), null, List.of(eid));
        DpmsrCreateRequest.Party party = new DpmsrCreateRequest.Party("Walk-in buyer", null, null, buyer);

        DpmsrReportInput input = mapper.toInput(req(party), 3177);

        assertThat(input.parties().get(0).getPersonMyClient()).isNotNull();
        assertThat(input.parties().get(0).getPersonMyClient().getIdentifications().getIdentification()).hasSize(1);
        assertThat(input.parties().get(0).getEntity()).isNull();
    }

    @Test
    void partyWithNeitherEntityNorPersonThrows() {
        DpmsrCreateRequest.Party empty = new DpmsrCreateRequest.Party("x", null, null, null);
        assertThatThrownBy(() -> mapper.toInput(req(empty), 3177))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static DpmsrCreateRequest req(DpmsrCreateRequest.Party party) {
        DpmsrCreateRequest.Person mlro = new DpmsrCreateRequest.Person(
                null, "Sara", "Khan", null, null, null, null, null, null, null, null, null);
        DpmsrCreateRequest.Goods gold = new DpmsrCreateRequest.Goods(
                "GOLD", null, null, null, null, new BigDecimal("90000.00"), "AED", null, null, null);
        return new DpmsrCreateRequest(null, "PAY-X", odt("2026-06-02T12:00:00"), null, "r", "a",
                List.of("DPMSJ"), mlro, null, List.of(party), List.of(gold));
    }

    private static OffsetDateTime odt(String isoLocal) {
        return OffsetDateTime.parse(isoLocal + "Z").withOffsetSameInstant(ZoneOffset.UTC);
    }
}
