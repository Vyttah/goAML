package com.vyttah.goaml.service.integration;

import com.vyttah.goaml.model.dto.integration.ScreeningPartyPayload;
import com.vyttah.goaml.model.dto.integration.ScreeningPartyPayload.Address;
import com.vyttah.goaml.model.dto.integration.ScreeningPartyPayload.Identification;
import com.vyttah.goaml.model.dto.integration.ScreeningPartyPayload.LegalCustomer;
import com.vyttah.goaml.model.dto.integration.ScreeningPartyPayload.NaturalCustomer;
import com.vyttah.goaml.model.dto.integration.ScreeningPartyPayload.Phone;
import com.vyttah.goaml.model.dto.integration.ScreeningPartyPayload.RelatedParty;
import com.vyttah.goaml.model.dto.integration.ScreeningPartyPayload.Sanctions;
import com.vyttah.goaml.model.dto.integration.ScreeningPartyPayload.SubjectType;
import com.vyttah.goaml.model.dto.report.DpmsrCreateRequest;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 1.5c.1 — screening profile → goAML party set: natural → Person, legal → Entity (+ directors), shareholders /
 * UBOs → additional parties, and the PEP / sanctions verdict → the customer party's comments.
 */
class ScreeningPartyMapperTest {

    @Test
    void naturalCustomerMapsToPersonWithIdentificationAndPep() {
        NaturalCustomer nat = new NaturalCustomer("M", "John", "Doe", "JD",
                LocalDate.of(1985, 3, 1), "IN", "IN", "AE", null, "Trader", "j@x.test",
                new Phone("+971", "500000000"),
                new Address("1 St", "Dubai", "AE", null),
                List.of(new Identification("PASSPORT", "P123", null, LocalDate.of(2030, 1, 1), "IN")),
                true);
        ScreeningPartyPayload p = new ScreeningPartyPayload(7, "CUST-1", SubjectType.NATURAL,
                nat, null, null, null, null, null);

        List<DpmsrCreateRequest.Party> parties = ScreeningPartyMapper.toParties(p);

        assertThat(parties).hasSize(1);
        DpmsrCreateRequest.Party party = parties.get(0);
        assertThat(party.reason()).isEqualTo("Customer");
        assertThat(party.entity()).isNull();
        assertThat(party.person().firstName()).isEqualTo("John");
        assertThat(party.person().lastName()).isEqualTo("Doe");
        assertThat(party.person().idNumber()).isEqualTo("P123");        // from the identification
        assertThat(party.person().identifications().get(0).type()).isEqualTo("PASSPORT");
        assertThat(party.comments()).contains("PEP");
    }

    @Test
    void emiratesIdIsPreferredAsPersonIdNumber() {
        NaturalCustomer nat = new NaturalCustomer(null, "Jane", "Smith", null, null, "AE", null, null,
                "784-1990-1234567-1", null, null, null, null,
                List.of(new Identification("PASSPORT", "P999", null, null, "GB")), false);
        ScreeningPartyPayload p = new ScreeningPartyPayload(7, "CUST-2", SubjectType.NATURAL,
                nat, null, null, null, null, null);

        DpmsrCreateRequest.Party party = ScreeningPartyMapper.toParties(p).get(0);

        assertThat(party.person().idNumber()).isEqualTo("784-1990-1234567-1");
        assertThat(party.comments()).isNull();   // not PEP, no sanctions
    }

    @Test
    void legalCustomerMapsToEntityWithDirectors() {
        LegalCustomer legal = new LegalCustomer("Acme Trading FZE", "Acme", "INC-1", "Dubai", "AE",
                LocalDate.of(2010, 1, 1), "LIC-9", "TRN-1", new Phone("+971", "44"), null);
        RelatedParty director = new RelatedParty("NATURAL", "Ravi Patel", null, null,
                LocalDate.of(1970, 5, 5), "IN", "AE", false, null, "PASSPORT", "D-1", "IN",
                null, null, null, null);
        ScreeningPartyPayload p = new ScreeningPartyPayload(7, "LEG-1", SubjectType.LEGAL,
                null, legal, List.of(director), null, null, null);

        DpmsrCreateRequest.Party party = ScreeningPartyMapper.toParties(p).get(0);

        assertThat(party.person()).isNull();
        assertThat(party.entity().name()).isEqualTo("Acme Trading FZE");
        assertThat(party.entity().incorporationNumber()).isEqualTo("INC-1");
        assertThat(party.entity().incorporationCountryCode()).isEqualTo("AE");
        assertThat(party.entity().directors()).hasSize(1);
        assertThat(party.entity().directors().get(0).firstName()).isEqualTo("Ravi");
        assertThat(party.entity().directors().get(0).lastName()).isEqualTo("Patel");
        assertThat(party.entity().directors().get(0).role()).isEqualTo("Director");
    }

    @Test
    void incorporationNumberFallsBackToLicenseNumber() {
        LegalCustomer legal = new LegalCustomer("Beta LLC", null, null, null, "AE", null,
                "LIC-77", null, null, null);
        ScreeningPartyPayload p = new ScreeningPartyPayload(7, "LEG-2", SubjectType.LEGAL,
                null, legal, null, null, null, null);

        DpmsrCreateRequest.Party party = ScreeningPartyMapper.toParties(p).get(0);

        assertThat(party.entity().incorporationNumber()).isEqualTo("LIC-77");
        assertThat(party.entity().directors()).isNull();
    }

    @Test
    void shareholdersAndUbosBecomeAdditionalParties() {
        LegalCustomer legal = new LegalCustomer("Acme Trading FZE", null, "INC-1", null, "AE", null,
                null, null, null, null);
        RelatedParty shNatural = new RelatedParty("NATURAL", "Mona Ali", null, null, null, "AE", null,
                true, new BigDecimal("40.0"), null, null, null, null, null, null, null);
        RelatedParty shLegal = new RelatedParty("LEGAL", null, null, null, null, null, null,
                false, new BigDecimal("60"), null, null, null, "Holdco Ltd", "H-1", "GB", null);
        RelatedParty ubo = new RelatedParty("NATURAL", "Sam Owner", null, null, null, "US", null,
                false, null, "PASSPORT", "U-1", "US", null, null, null, null);
        ScreeningPartyPayload p = new ScreeningPartyPayload(7, "LEG-3", SubjectType.LEGAL,
                null, legal, null, List.of(shNatural, shLegal), List.of(ubo), null);

        List<DpmsrCreateRequest.Party> parties = ScreeningPartyMapper.toParties(p);

        assertThat(parties).hasSize(4); // customer + 2 shareholders + 1 UBO
        assertThat(parties).extracting(DpmsrCreateRequest.Party::reason)
                .containsExactly("Customer", "Shareholder", "Shareholder", "Beneficial Owner");
        DpmsrCreateRequest.Party shN = parties.get(1);
        assertThat(shN.person().firstName()).isEqualTo("Mona");
        assertThat(shN.comments()).contains("40%").contains("PEP");
        DpmsrCreateRequest.Party shL = parties.get(2);
        assertThat(shL.entity().name()).isEqualTo("Holdco Ltd");
        assertThat(shL.comments()).contains("60%");
        DpmsrCreateRequest.Party uboParty = parties.get(3);
        assertThat(uboParty.person().lastName()).isEqualTo("Owner");
        assertThat(uboParty.person().identifications().get(0).number()).isEqualTo("U-1");
    }

    @Test
    void sanctionsHitsRenderIntoCustomerComments() {
        LegalCustomer legal = new LegalCustomer("Risky FZE", null, "INC-9", null, "AE", null, null, null, null, null);
        Sanctions s = new Sanctions(true, List.of(
                new Sanctions.Hit("Risky FZE", 95, "OFAC", "SANCTIONS", "PART", "match"),
                new Sanctions.Hit("Risky F", 80, "UN", "SANCTIONS", "PART", "match")));
        ScreeningPartyPayload p = new ScreeningPartyPayload(7, "LEG-4", SubjectType.LEGAL,
                null, legal, null, null, null, s);

        String comments = ScreeningPartyMapper.toParties(p).get(0).comments();

        assertThat(comments).contains("2 hit(s)")
                .contains("Risky FZE (OFAC, score 95, SANCTIONS)")
                .contains("Risky F (UN, score 80, SANCTIONS)");
    }
}
