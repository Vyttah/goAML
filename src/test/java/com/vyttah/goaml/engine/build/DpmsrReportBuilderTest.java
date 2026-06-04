package com.vyttah.goaml.engine.build;

import com.vyttah.goaml.domain.generated.ActivityType;
import com.vyttah.goaml.domain.generated.CurrencyType;
import com.vyttah.goaml.domain.generated.EntityPersonRoleType;
import com.vyttah.goaml.domain.generated.Report;
import com.vyttah.goaml.domain.generated.ReportType;
import com.vyttah.goaml.domain.generated.TAddress;
import com.vyttah.goaml.domain.generated.TEntity;
import com.vyttah.goaml.domain.generated.TPerson;
import com.vyttah.goaml.domain.generated.TPersonIdentification;
import com.vyttah.goaml.domain.generated.TPersonMyClient;
import com.vyttah.goaml.domain.generated.TPersonRegistrationInReport;
import com.vyttah.goaml.domain.generated.TPhone;
import com.vyttah.goaml.domain.generated.TTransItem;
import com.vyttah.goaml.engine.jurisdiction.JurisdictionRegistry;
import com.vyttah.goaml.engine.lookup.LookupService;
import com.vyttah.goaml.engine.marshal.ReportMarshaller;
import com.vyttah.goaml.engine.validation.ReportValidator;
import com.vyttah.goaml.engine.validation.XsdSchemaValidator;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Step 6 proof: {@link DpmsrReportBuilder} builds a DPMSR that is BOTH ReportValidator-clean and XSD-valid,
 * across the full DPMSR field surface (entity party with multiple directors + a second party of a different
 * subject + multiple goods lines), via both the record and fluent entry forms — and a minimal DPMSR is also
 * valid (optional fields really are optional).
 */
class DpmsrReportBuilderTest {

    private final DpmsrReportBuilder builder = new DpmsrReportBuilder(
            new ActivityReportBuilder(),
            new ReportValidator(new JurisdictionRegistry(), new LookupService()),
            new XsdSchemaValidator(),
            new ReportMarshaller());

    @Test
    void maximalDpmsrBuildsValidViaRecord() {
        ValidatedReport result = builder.buildAndValidate(maximalInput(), "ae");

        assertThat(result.rules().isValid())
                .as("business rules; errors=%s", result.rules().errors()).isTrue();
        assertThat(result.xsd().isValid())
                .as("XSD; errors=%s", result.xsd().errors()).isTrue();
        assertThat(result.isValid()).isTrue();

        // report is DPMSR-shaped: activity in the choice slot, with parties + goods
        Report report = result.report();
        assertThat(report.getReportCode()).isEqualTo(ReportType.DPMSR);
        assertThat(report.getCurrencyCodeLocal()).isEqualTo(CurrencyType.AED);
        ActivityType activity = report.getReportActivity();
        assertThat(activity.getReportParties().getReportParty()).hasSize(2);
        assertThat(activity.getGoodsServices().getItem()).hasSize(2);
    }

    @Test
    void recordAndFluentFormsProduceEquivalentValidXml() {
        Report fromRecord = builder.build(maximalInput());
        Report fromFluent = builder.build(maximalInputFluent());

        String recordXml = marshal(fromRecord);
        String fluentXml = marshal(fromFluent);
        assertThat(fluentXml).isEqualTo(recordXml);

        // spot-check that set leaf fields survive into the XML
        assertThat(recordXml).contains("<incorporation_number>100000</incorporation_number>");
        assertThat(recordXml).contains("<role>ATR</role>");
        assertThat(recordXml).contains("<item_type>GOLD</item_type>");
        assertThat(recordXml).contains("<item_type>DIMND</item_type>"); // second goods line, different item_type
    }

    @Test
    void minimalDpmsrBuildsValid() {
        TEntity entity = new TEntity();
        entity.setName("Minimal Trading FZE");
        entity.setIncorporationNumber("123456");
        entity.setIncorporationCountryCode("AE");

        TTransItem gold = new TTransItem();
        gold.setItemType("GOLD");
        gold.setEstimatedValue(new BigDecimal("90000.00"));
        gold.setCurrencyCode(CurrencyType.AED);

        DpmsrReportInput input = DpmsrReportInput.builder()
                .rentityId(3177)
                .entityReference("MIN-0001")
                .submissionDate(odt("2026-06-02T12:00:00"))
                .reportingPerson(mlro())
                .reason("DPMS threshold met")
                .action("Filed")
                .indicators("DPMSJ")
                .party(GoamlParties.entity(entity, "Seller of gold above AED 55,000", null))
                .goods(gold)
                .build();

        ValidatedReport result = builder.buildAndValidate(input, "ae");
        assertThat(result.isValid())
                .as("minimal DPMSR valid; rules=%s xsd=%s", result.rules().errors(), result.xsd().errors())
                .isTrue();
    }

    // ---------- inputs ----------

    private DpmsrReportInput maximalInput() {
        return new DpmsrReportInput(3177, "DXB-MAIN", "PAY 0001 INV 0001", odt("2026-06-02T12:00:00"), null,
                mlro(), location(),
                "Cash purchase of gold and diamonds above AED 55,000",
                "Filed DPMSR per MOE mandate",
                List.of("DPMSJ"),
                List.of(
                        GoamlParties.entity(jewelleryEntity(), "Seller above AED 55,000", "Purchase of gold and cash paid"),
                        GoamlParties.personMyClient(walkInBuyer(), "Walk-in cash buyer", null)),
                List.of(goldItem(), diamondItem()));
    }

    private DpmsrReportInput maximalInputFluent() {
        return DpmsrReportInput.builder()
                .rentityId(3177).rentityBranch("DXB-MAIN").entityReference("PAY 0001 INV 0001")
                .submissionDate(odt("2026-06-02T12:00:00"))
                .reportingPerson(mlro()).location(location())
                .reason("Cash purchase of gold and diamonds above AED 55,000")
                .action("Filed DPMSR per MOE mandate")
                .indicators("DPMSJ")
                .party(GoamlParties.entity(jewelleryEntity(), "Seller above AED 55,000", "Purchase of gold and cash paid"))
                .party(GoamlParties.personMyClient(walkInBuyer(), "Walk-in cash buyer", null))
                .goods(goldItem(), diamondItem())
                .build();
    }

    // ---------- leaf builders ----------

    private TEntity jewelleryEntity() {
        TEntity e = new TEntity();
        e.setName("Example Jewellery LLC");
        e.setCommercialName("Example Jewellery LLC");
        e.setIncorporationNumber("100000");
        e.setIncorporationState("DUBAI");
        e.setIncorporationCountryCode("AE");
        e.setPhones(GoamlWrappers.wrap(new TEntity.Phones(), TEntity.Phones::getPhone,
                phone("BU", "L", "971", "500000000")));
        e.getDirectorId().add(director("Ali", "Hassan", "Z0000000"));
        e.getDirectorId().add(director("Aisha", "Mahmoud", "Z9988776")); // a second director — coverage
        return e;
    }

    private TEntity.DirectorId director(String first, String last, String passport) {
        TEntity.DirectorId d = new TEntity.DirectorId();
        d.setGender("M");
        d.setFirstName(first);
        d.setLastName(last);
        d.setBirthdate(odt("1990-01-01T12:00:00"));
        d.setPassportNumber(passport);
        d.setPassportCountry("IN");
        d.setIdNumber(passport);
        d.setNationality1("IN");
        d.setResidence("AE");
        d.setPhones(GoamlWrappers.wrap(new TPerson.Phones(), TPerson.Phones::getPhone,
                phone("BU", "L", "971", "500000000")));
        d.setRole(EntityPersonRoleType.ATR);
        return d;
    }

    private TPersonMyClient walkInBuyer() {
        TPersonMyClient p = new TPersonMyClient();
        p.setGender("M");
        p.setFirstName("Mohamad");
        p.setLastName("Ali Al-Jaber");
        p.setBirthdate(odt("1985-03-12T00:00:00"));
        p.setNationality1("AE");
        p.setResidence("AE");
        p.setIdNumber("784198012345678");
        p.setTaxRegNumber("Y");
        p.setPhones(GoamlWrappers.wrap(new TPersonMyClient.Phones(), TPersonMyClient.Phones::getPhone,
                phone("PRIVT", "L", "971", "501112233")));
        TPersonIdentification eid = new TPersonIdentification();
        eid.setType("EID");
        eid.setNumber("784198012345678");
        eid.setIssueDate(odt("2020-01-15T00:00:00"));
        eid.setExpiryDate(odt("2030-01-14T00:00:00"));
        eid.setIssueCountry("AE");
        p.setIdentifications(GoamlWrappers.wrap(new TPersonMyClient.Identifications(),
                TPersonMyClient.Identifications::getIdentification, eid));
        return p;
    }

    private TTransItem goldItem() {
        TTransItem i = new TTransItem();
        i.setItemType("GOLD");
        i.setDescription("1kg gold bullion bar, .9999 fine");
        i.setEstimatedValue(new BigDecimal("75000.00"));
        i.setCurrencyCode(CurrencyType.AED);
        i.setSize(new BigDecimal("1000"));
        i.setSizeUom("GRAM");
        i.setRegistrationDate(odt("2026-06-02T12:00:00"));
        return i;
    }

    private TTransItem diamondItem() {
        TTransItem i = new TTransItem();
        i.setItemType("DIMND");
        i.setDescription("Loose polished diamond, 5ct");
        i.setEstimatedValue(new BigDecimal("60000.00"));
        i.setCurrencyCode(CurrencyType.AED);
        i.setSize(new BigDecimal("5"));
        i.setSizeUom("CARAT");
        return i;
    }

    private TPersonRegistrationInReport mlro() {
        TPersonRegistrationInReport p = new TPersonRegistrationInReport();
        p.setGender("F");
        p.setFirstName("Sara");
        p.setLastName("Khan");
        p.setNationality1("AE");
        p.setResidence("AE");
        p.setIdNumber("784199012345678");
        p.setOccupation("MLRO");
        p.setPhones(GoamlWrappers.wrap(new TPersonRegistrationInReport.Phones(),
                TPersonRegistrationInReport.Phones::getPhone, phone("BU", "L", "971", "44441234")));
        p.setAddresses(GoamlWrappers.wrap(new TPersonRegistrationInReport.Addresses(),
                TPersonRegistrationInReport.Addresses::getAddress,
                address("BU", "Office 501, Compliance Tower", "Dubai", "AE", "Dubai")));
        return p;
    }

    private TAddress location() {
        return address("BU", "Showroom, Dubai Gold Souk", "Dubai", "AE", "Dubai");
    }

    private TPhone phone(String contact, String comm, String prefix, String number) {
        TPhone p = new TPhone();
        p.setTphContactType(contact);
        p.setTphCommunicationType(comm);
        p.setTphCountryPrefix(prefix);
        p.setTphNumber(number);
        return p;
    }

    private TAddress address(String type, String line, String city, String country, String state) {
        TAddress a = new TAddress();
        a.setAddressType(type);
        a.setAddress(line);
        a.setCity(city);
        a.setCountryCode(country);
        a.setState(state);
        return a;
    }

    private String marshal(Report report) {
        return new String(new ReportMarshaller().marshal(report), StandardCharsets.UTF_8);
    }

    private static OffsetDateTime odt(String isoLocal) {
        return OffsetDateTime.parse(isoLocal + "Z").withOffsetSameInstant(ZoneOffset.UTC);
    }
}
