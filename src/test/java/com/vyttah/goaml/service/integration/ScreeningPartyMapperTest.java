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
import com.vyttah.goaml.model.mapper.report.DpmsrRequestMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
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
        ScreeningPartyPayload p = new ScreeningPartyPayload("7", "CUST-1", SubjectType.NATURAL,
                nat, null, null, null, null, null);

        List<DpmsrCreateRequest.Party> parties = ScreeningPartyMapper.toParties(p);

        assertThat(parties).hasSize(1);
        DpmsrCreateRequest.Party party = parties.get(0);
        assertThat(party.reason()).isEqualTo("Customer");
        assertThat(party.entity()).isNull();
        assertThat(party.person().firstName()).isEqualTo("John");
        assertThat(party.person().lastName()).isEqualTo("Doe");
        assertThat(party.person().gender()).isEqualTo("M");             // normalized to a valid code
        assertThat(party.person().countryOfBirth()).isEqualTo("IN");    // carried from the payload (not fabricated)
        assertThat(party.person().idNumber()).isEqualTo("P123");        // derived from the identification number
        // B4: the identification block, email and alias are now carried (previously dropped)
        assertThat(party.person().identifications()).hasSize(1);
        assertThat(party.person().identifications().get(0).type()).isEqualTo("PASSPORT");
        assertThat(party.person().identifications().get(0).number()).isEqualTo("P123");
        assertThat(party.person().email()).isEqualTo("j@x.test");
        assertThat(party.person().alias()).isEqualTo("JD");
        assertThat(party.comments()).contains("PEP");
    }

    @Test
    void emiratesIdIsPreferredAsPersonIdNumber() {
        NaturalCustomer nat = new NaturalCustomer(null, "Jane", "Smith", null, null, "AE", null, null,
                "784-1990-1234567-1", null, null, null, null,
                List.of(new Identification("PASSPORT", "P999", null, null, "GB")), false);
        ScreeningPartyPayload p = new ScreeningPartyPayload("7", "CUST-2", SubjectType.NATURAL,
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
        ScreeningPartyPayload p = new ScreeningPartyPayload("7", "LEG-1", SubjectType.LEGAL,
                null, legal, List.of(director), null, null, null);

        DpmsrCreateRequest.Party party = ScreeningPartyMapper.toParties(p).get(0);

        assertThat(party.person()).isNull();
        assertThat(party.entity().name()).isEqualTo("Acme Trading FZE");
        assertThat(party.entity().incorporationNumber()).isEqualTo("INC-1");
        assertThat(party.entity().incorporationCountryCode()).isEqualTo("AE");
        // B4: trn + incorporation date are now carried (previously had no slot → dropped)
        assertThat(party.entity().taxRegNumber()).isEqualTo("TRN-1");
        assertThat(party.entity().incorporationDate()).isNotNull();
        assertThat(party.entity().directors()).hasSize(1);
        assertThat(party.entity().directors().get(0).firstName()).isEqualTo("Ravi");
        assertThat(party.entity().directors().get(0).lastName()).isEqualTo("Patel");
        assertThat(party.entity().directors().get(0).role()).isEqualTo("Director");
    }

    @Test
    void incorporationNumberFallsBackToLicenseNumber() {
        LegalCustomer legal = new LegalCustomer("Beta LLC", null, null, null, "AE", null,
                "LIC-77", null, null, null);
        ScreeningPartyPayload p = new ScreeningPartyPayload("7", "LEG-2", SubjectType.LEGAL,
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
        ScreeningPartyPayload p = new ScreeningPartyPayload("7", "LEG-3", SubjectType.LEGAL,
                null, legal, null, List.of(shNatural, shLegal), List.of(ubo), null);

        List<DpmsrCreateRequest.Party> parties = ScreeningPartyMapper.toParties(p);

        assertThat(parties).hasSize(4); // customer + 2 shareholders + 1 UBO
        assertThat(parties).extracting(DpmsrCreateRequest.Party::reason)
                .containsExactly("Customer", "Shareholder", "Shareholder", "Beneficial Owner");
        DpmsrCreateRequest.Party shN = parties.get(1);
        assertThat(shN.person().firstName()).isEqualTo("Mona");
        assertThat(shN.person().gender()).isEqualTo("-");   // not provided → valid "not provided" code
        assertThat(shN.comments()).contains("40%").contains("PEP");
        DpmsrCreateRequest.Party shL = parties.get(2);
        assertThat(shL.entity().name()).isEqualTo("Holdco Ltd");
        assertThat(shL.comments()).contains("60%");
        DpmsrCreateRequest.Party uboParty = parties.get(3);
        assertThat(uboParty.person().lastName()).isEqualTo("Owner");
        assertThat(uboParty.person().idNumber()).isEqualTo("U-1");   // top-level id_number from the UBO's id
    }

    private static NaturalCustomer natural(String gender, String first, String last, java.time.LocalDate dob) {
        return new NaturalCustomer(gender, first, last, null, dob, null, null, null, null, null, null, null,
                null, null, false);
    }

    private static DpmsrCreateRequest.Person personOf(NaturalCustomer nat) {
        ScreeningPartyPayload p = new ScreeningPartyPayload("1", "U", SubjectType.NATURAL,
                nat, null, null, null, null, null);
        return ScreeningPartyMapper.toParties(p).get(0).person();
    }

    private static OffsetDateTime odt(String isoLocal) {
        return OffsetDateTime.parse(isoLocal + "Z").withOffsetSameInstant(ZoneOffset.UTC);
    }

    @Test
    void genderIsNormalisedAndUnknownDefaultsToDash() {
        assertThat(personOf(natural("F", "Ann", "Lee", null)).gender()).isEqualTo("F");
        assertThat(personOf(natural("male", "Bob", "Ng", null)).gender()).isEqualTo("-");
        assertThat(personOf(natural(null, "Cy", "Ho", null)).gender()).isEqualTo("-");
    }

    @Test
    void missingCustomerNamesAreOmittedNotFabricated() {
        // B4: a missing legal/natural body OMITS the name in the filing (→ INVALID at the gate, never filed)
        // rather than fabricating "Unknown" in the FIU XML. The human-facing displayName label still reads
        // "Unknown" (it is a UI label, never marshalled).
        ScreeningPartyPayload legalNoBody = new ScreeningPartyPayload("1", "X", SubjectType.LEGAL,
                null, null, null, null, null, null);
        assertThat(ScreeningPartyMapper.toParties(legalNoBody).get(0).entity().name()).isNull();
        assertThat(ScreeningPartyMapper.displayName(legalNoBody)).isEqualTo("Unknown");

        ScreeningPartyPayload naturalNoBody = new ScreeningPartyPayload("1", "X", SubjectType.NATURAL,
                null, null, null, null, null, null);
        assertThat(ScreeningPartyMapper.toParties(naturalNoBody).get(0).person().firstName()).isNull();
        assertThat(ScreeningPartyMapper.displayName(naturalNoBody)).isEqualTo("Unknown");
    }

    @Test
    void sparseNaturalCustomerOmitsCleanlyWithNoFabricatedCountryOrResidence() {
        // B4: a sparse profile (nationality only, no countryOfBirth/residence) must NOT fabricate
        // countryOfBirth←nationality or residence←nationality — those are omitted.
        NaturalCustomer sparse = new NaturalCustomer(null, "Aman", "Roy", null, null, "IN", null, null,
                null, null, null, null, null, null, false);
        DpmsrCreateRequest.Person person = personOf(sparse);
        assertThat(person.nationality()).isEqualTo("IN");
        assertThat(person.countryOfBirth()).isNull();
        assertThat(person.residence()).isNull();
        assertThat(person.email()).isNull();
        assertThat(person.identifications()).isNull();
    }

    @Test
    void relatedPartyFullNameSplitsAndCleanCustomerHasNoComments() {
        RelatedParty oneName = new RelatedParty("NATURAL", "Cher", null, null, null, "AE", null,
                false, null, null, null, null, null, null, null, null);
        LegalCustomer legal = new LegalCustomer("Clean FZE", null, "INC", null, "AE", null, null, null, null, null);
        ScreeningPartyPayload p = new ScreeningPartyPayload("1", "C", SubjectType.LEGAL,
                null, legal, null, List.of(oneName), null, null);

        List<DpmsrCreateRequest.Party> parties = ScreeningPartyMapper.toParties(p);
        assertThat(parties.get(0).comments()).isNull();                 // no PEP, no sanctions
        assertThat(parties.get(1).person().firstName()).isEqualTo("Cher");
        assertThat(parties.get(1).person().lastName()).isNull();        // single-token name → omit, not "Unknown"
        assertThat(parties.get(1).comments()).isNull();                 // no %/PEP
    }

    @Test
    void pepOnlyCustomerCommentsAndHitTruncation() {
        // PEP but no sanctions → "PEP." only
        NaturalCustomer pep = new NaturalCustomer(null, "P", "Q", null, null, null, null, null, null, null,
                null, null, null, null, true);
        assertThat(ScreeningPartyMapper.sanctionsContext(new ScreeningPartyPayload("1", "P", SubjectType.NATURAL,
                pep, null, null, null, null, null))).isEqualTo("PEP.");

        // >5 hits → "+N more"
        List<Sanctions.Hit> hits = new java.util.ArrayList<>();
        for (int i = 0; i < 7; i++) {
            hits.add(new Sanctions.Hit("H" + i, i, null, null, null, null));
        }
        LegalCustomer legal = new LegalCustomer("Z FZE", null, "I", null, "AE", null, null, null, null, null);
        String ctx = ScreeningPartyMapper.sanctionsContext(new ScreeningPartyPayload("1", "Z", SubjectType.LEGAL,
                null, legal, null, null, null, new Sanctions(true, hits)));
        assertThat(ctx).contains("7 hit(s)").contains("+2 more");
    }

    @Test
    void sanctionsHitsRenderIntoCustomerComments() {
        LegalCustomer legal = new LegalCustomer("Risky FZE", null, "INC-9", null, "AE", null, null, null, null, null);
        Sanctions s = new Sanctions(true, List.of(
                new Sanctions.Hit("Risky FZE", 95, "OFAC", "SANCTIONS", "PART", "match"),
                new Sanctions.Hit("Risky F", 80, "UN", "SANCTIONS", "PART", "match")));
        ScreeningPartyPayload p = new ScreeningPartyPayload("7", "LEG-4", SubjectType.LEGAL,
                null, legal, null, null, null, s);

        String comments = ScreeningPartyMapper.toParties(p).get(0).comments();

        assertThat(comments).contains("2 hit(s)")
                .contains("Risky FZE (OFAC, score 95, SANCTIONS)")
                .contains("Risky F (UN, score 80, SANCTIONS)");
    }

    // ---------- B4: end-to-end — carried fields reach the (XSD-VALID) XML, sparse omits cleanly ----------

    private static final DpmsrReportBuilder BUILDER = new DpmsrReportBuilder(
            new ActivityReportBuilder(),
            new ReportValidator(new JurisdictionRegistry(), new LookupService()),
            new XsdSchemaValidator(),
            new ReportMarshaller());

    private static ValidatedReport buildFrom(ScreeningPartyPayload payload) {
        List<DpmsrCreateRequest.Party> parties = ScreeningPartyMapper.toParties(payload);
        DpmsrCreateRequest.Person mlro = new DpmsrCreateRequest.Person(
                null, "Sara", "Khan", null, null, null, null, null, null, null, null, null, null);
        DpmsrCreateRequest.Goods gold = new DpmsrCreateRequest.Goods(
                "GOLD", null, "bar", null, null, new BigDecimal("90000.00"), "AED", null, null, null,
                null, null, null, null);
        DpmsrCreateRequest req = new DpmsrCreateRequest(null, "SCR-E2E", odt("2026-06-02T12:00:00"), null,
                "DPMS", "Filed", List.of("DPMSJ"), mlro, null, parties, List.of(gold));
        DpmsrReportInput input = new DpmsrRequestMapper().toInput(req, 3177);
        return BUILDER.buildAndValidate(input, "ae");
    }

    @Test
    void fullyPopulatedScreeningPayloadCarriesAllFieldsIntoTheXml() {
        // These fields were previously DROPPED by the curated path; assert each now reaches the marshalled
        // XML. NB: a goAML identification needs a coded `type` (PASSP, not PASSPORT) and the screening Phone /
        // Address records carry no contact-type / address-type slot, so this rich payload is not itself
        // XSD-VALID — the residual screening-DTO type-code gap is noted in the remediation summary. What this
        // proves is that the values are carried through (no silent loss), which is the B4 fix.
        NaturalCustomer nat = new NaturalCustomer("M", "John", "Doe", "Johnny",
                LocalDate.of(1985, 3, 1), "IN", "IN", "AE", "784", "Trader", "john@x.test",
                new Phone("+971", "500000000"), new Address("1 St", "Dubai", "AE", "Dubai"),
                List.of(new Identification("PASSP", "P123", LocalDate.of(2020, 1, 1),
                        LocalDate.of(2030, 1, 1), "IN")), false);
        ScreeningPartyPayload p = new ScreeningPartyPayload("7", "CUST-E2E", SubjectType.NATURAL,
                nat, null, null, null, null, null);

        String xml = buildFrom(p).xml();
        assertThat(xml)
                .contains("<person>")
                .contains("john@x.test")          // email carried (was dropped)
                .contains("Johnny")               // alias carried (was dropped)
                .contains("<identification>")     // identification block carried (was dropped)
                .contains("P123")
                .contains("<addresses>");         // person address carried (was dropped)

        // legal customer carries trn + incorporation_date + address (all previously dropped)
        LegalCustomer legal = new LegalCustomer("Acme Trading FZE", "Acme", "INC-1", "Dubai", "AE",
                LocalDate.of(2010, 1, 1), "LIC-9", "TRN-1", new Phone("+971", "44"),
                new Address("HQ Tower", "Dubai", "AE", "Dubai"));
        ScreeningPartyPayload pl = new ScreeningPartyPayload("7", "LEG-E2E", SubjectType.LEGAL,
                null, legal, null, null, null, null);
        assertThat(buildFrom(pl).xml())
                .contains("<tax_reg_number>TRN-1</tax_reg_number>")
                .contains("<incorporation_date>")
                .contains("HQ Tower");
    }

    @Test
    void sparseScreeningPayloadOmitsCleanlyWithNoUnknownOrFabricatedCountry() {
        // a sparse natural customer: just names + nationality
        NaturalCustomer nat = new NaturalCustomer("M", "John", "Doe", null, null, "IN", null, null,
                "784", null, null, null, null, null, false);
        ScreeningPartyPayload p = new ScreeningPartyPayload("7", "CUST-SPARSE", SubjectType.NATURAL,
                nat, null, null, null, null, null);

        ValidatedReport vr = buildFrom(p);
        assertThat(vr.isValid()).as("errors=%s", vr.xsd().errors()).isTrue();
        String xml = vr.xml();
        // no fabricated "Unknown" name and no fabricated country_of_birth/residence in the filed XML
        assertThat(xml).doesNotContain("Unknown");
        assertThat(xml).doesNotContain("<country_of_birth>");
        assertThat(xml).doesNotContain("<residence>");
    }
}
