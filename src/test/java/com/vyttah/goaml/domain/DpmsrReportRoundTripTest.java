package com.vyttah.goaml.domain;

import com.vyttah.goaml.domain.activity.Activity;
import com.vyttah.goaml.domain.activity.ReportParty;
import com.vyttah.goaml.domain.common.GoodsServices;
import com.vyttah.goaml.domain.common.ReportIndicator;
import com.vyttah.goaml.domain.common.ReportingPerson;
import com.vyttah.goaml.domain.enums.ReportCode;
import com.vyttah.goaml.domain.enums.SubmissionCode;
import com.vyttah.goaml.domain.party.TAddress;
import com.vyttah.goaml.domain.party.TPerson;
import com.vyttah.goaml.domain.party.TPersonIdentification;
import com.vyttah.goaml.domain.party.TPersonMyClient;
import com.vyttah.goaml.domain.party.TPhone;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.Marshaller;
import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 3 verification — round-trips a representative DPMSR (activity-based) report through
 * JAXB marshal + unmarshal, asserting element ordering, key values, and structural fidelity.
 *
 * <p>If this passes, the hand-modeled POJOs faithfully represent the goAML v4.0 schema for
 * the activity report shape used by UAE's DPMSR. The transaction shape gets its own test
 * in a sibling class — together they exercise both report shapes the engine builds in Phase 4.
 */
class DpmsrReportRoundTripTest {

    @Test
    void dpmsrActivityReportRoundTripsThroughJaxb() throws Exception {
        Report report = buildSampleDpmsrReport();

        String xml = marshal(report);

        // root and overall ordering: header fields precede activity, activity precedes report_indicators
        assertThat(xml)
                .contains("<report>")
                .contains("</report>")
                .contains("<report_code>DPMSR</report_code>")
                .contains("<currency_code_local>AED</currency_code_local>")
                .contains("<submission_date>2026-05-26T10:00:00</submission_date>")
                .contains("<entity_reference>DPMSR-2026-001</entity_reference>");

        int rentityIdIdx        = xml.indexOf("<rentity_id>");
        int submissionCodeIdx   = xml.indexOf("<submission_code>");
        int reportCodeIdx       = xml.indexOf("<report_code>");
        int submissionDateIdx   = xml.indexOf("<submission_date>");
        int reportingPersonIdx  = xml.indexOf("<reporting_person>");
        int activityIdx         = xml.indexOf("<activity>");
        int reportIndicatorsIdx = xml.indexOf("<report_indicators>");

        assertThat(rentityIdIdx).isPositive();
        assertThat(submissionCodeIdx).isGreaterThan(rentityIdIdx);
        assertThat(reportCodeIdx).isGreaterThan(submissionCodeIdx);
        assertThat(submissionDateIdx).isGreaterThan(reportCodeIdx);
        assertThat(reportingPersonIdx).isGreaterThan(submissionDateIdx);
        assertThat(activityIdx).isGreaterThan(reportingPersonIdx);
        assertThat(reportIndicatorsIdx).isGreaterThan(activityIdx);

        // Activity inner structure: report_parties → goods_services
        int reportPartiesIdx  = xml.indexOf("<report_parties>");
        int goodsServicesIdx  = xml.indexOf("<goods_services>");
        assertThat(reportPartiesIdx).isPositive();
        assertThat(goodsServicesIdx).isGreaterThan(reportPartiesIdx);

        // Round-trip: unmarshal the XML we just produced and verify equivalence.
        Report parsed = unmarshal(xml);

        assertThat(parsed.getReportCode()).isEqualTo(ReportCode.DPMSR);
        assertThat(parsed.getSubmissionCode()).isEqualTo(SubmissionCode.E);
        assertThat(parsed.getRentityId()).isEqualTo(101);
        assertThat(parsed.getEntityReference()).isEqualTo("DPMSR-2026-001");
        assertThat(parsed.getCurrencyCodeLocal()).isEqualTo("AED");

        assertThat(parsed.getActivity()).isNotNull();
        assertThat(parsed.getActivity().getReportParties()).hasSize(1);
        ReportParty buyer = parsed.getActivity().getReportParties().get(0);
        assertThat(buyer.getReason()).contains("Cash purchase");
        assertThat(buyer.getPersonMyClient()).isNotNull();
        assertThat(buyer.getPersonMyClient().getFirstName()).isEqualTo("Mohamad");
        assertThat(buyer.getPersonMyClient().getLastName()).isEqualTo("Ali Al-Jaber");
        assertThat(buyer.getPersonMyClient().getNationality1()).isEqualTo("AE");

        assertThat(parsed.getActivity().getGoodsServices()).hasSize(1);
        GoodsServices gold = parsed.getActivity().getGoodsServices().get(0);
        assertThat(gold.getItemType()).isEqualTo("GOLD");
        assertThat(gold.getEstimatedValue()).isEqualByComparingTo("75000.00");
        assertThat(gold.getCurrencyCode()).isEqualTo("AED");
        assertThat(gold.getSizeUom()).isEqualTo("GRAM");

        assertThat(parsed.getReportIndicators()).hasSize(1);
        assertThat(parsed.getReportIndicators().get(0).getValue()).isEqualTo("DPMSR_CASH_THRESHOLD");
    }

    // ----- sample builder -----

    private Report buildSampleDpmsrReport() {
        TAddress buyerAddress = new TAddress();
        buyerAddress.setAddressType("PRIVT");
        buyerAddress.setAddress("Villa 12, Al Wasl Road");
        buyerAddress.setCity("Dubai");
        buyerAddress.setCountryCode("AE");
        buyerAddress.setState("Dubai");

        TPhone buyerPhone = new TPhone();
        buyerPhone.setContactType("PRIVT");
        buyerPhone.setCountryPrefix("971");
        buyerPhone.setNumber("501234567");

        TPersonIdentification eid = new TPersonIdentification();
        eid.setType("EID");
        eid.setNumber("784198012345678");
        eid.setIssueDate(odt("2020-01-15T00:00:00"));
        eid.setExpiryDate(odt("2030-01-14T00:00:00"));
        eid.setIssueCountry("AE");

        TPersonMyClient buyer = new TPersonMyClient();
        buyer.setGender("M");
        buyer.setFirstName("Mohamad");
        buyer.setLastName("Ali Al-Jaber");
        buyer.setNationality1("AE");
        buyer.setResidence("AE");
        buyer.setIdNumber("784198012345678");
        buyer.setPhones(List.of(buyerPhone));
        buyer.setAddresses(List.of(buyerAddress));
        buyer.setIdentifications(List.of(eid));

        ReportParty party = new ReportParty();
        party.setSignificance(8);
        party.setReason("Cash purchase of gold bullion above the AED 55,000 threshold");
        party.setPersonMyClient(buyer);

        GoodsServices gold = new GoodsServices();
        gold.setItemType("GOLD");
        gold.setItemMake("Emirates Gold DMCC");
        gold.setDescription("1kg gold bullion bar, .9999 fine");
        gold.setEstimatedValue(new BigDecimal("75000.00"));
        gold.setCurrencyCode("AED");
        gold.setSize(new BigDecimal("1000"));
        gold.setSizeUom("GRAM");
        gold.setStatusCode("SOLD");
        gold.setPresentlyRegisteredTo("Mohamad Ali Al-Jaber");

        Activity activity = new Activity();
        activity.setReportParties(new ArrayList<>(List.of(party)));
        activity.setGoodsServices(new ArrayList<>(List.of(gold)));

        TPhone mlroPhone = new TPhone();
        mlroPhone.setContactType("BU");
        mlroPhone.setCountryPrefix("971");
        mlroPhone.setNumber("44441234");

        TAddress mlroAddress = new TAddress();
        mlroAddress.setAddressType("BU");
        mlroAddress.setAddress("Office 501, Gold Tower");
        mlroAddress.setCity("Dubai");
        mlroAddress.setCountryCode("AE");
        mlroAddress.setState("Dubai");

        ReportingPerson mlro = new ReportingPerson();
        mlro.setGender("F");
        mlro.setFirstName("Sara");
        mlro.setLastName("Khan");
        mlro.setNationality1("AE");
        mlro.setResidence("AE");
        mlro.setIdNumber("784199012345678");
        mlro.setEmail("mlro@alpha-jewellers.ae");
        mlro.setPhones(List.of(mlroPhone));
        mlro.setAddresses(List.of(mlroAddress));

        TAddress reportLocation = new TAddress();
        reportLocation.setAddressType("BU");
        reportLocation.setAddress("Showroom, Dubai Gold Souk");
        reportLocation.setCity("Dubai");
        reportLocation.setCountryCode("AE");
        reportLocation.setState("Dubai");

        ReportIndicator indicator = new ReportIndicator();
        indicator.setValue("DPMSR_CASH_THRESHOLD");

        Report report = new Report();
        report.setRentityId(101);
        report.setRentityBranch("DXB-MAIN");
        report.setSubmissionCode(SubmissionCode.E);
        report.setReportCode(ReportCode.DPMSR);
        report.setEntityReference("DPMSR-2026-001");
        report.setSubmissionDate(odt("2026-05-26T10:00:00"));
        report.setCurrencyCodeLocal("AED");
        report.setReportingPerson(mlro);
        report.setLocation(reportLocation);
        report.setReason("DPMS cash transaction reporting threshold met");
        report.setAction("Customer due diligence completed; transaction filed");
        report.setActivity(activity);
        report.setReportIndicators(new ArrayList<>(List.of(indicator)));
        return report;
    }

    private static OffsetDateTime odt(String isoLocal) {
        return OffsetDateTime.parse(isoLocal + "Z").withOffsetSameInstant(ZoneOffset.UTC);
    }

    // ----- JAXB helpers -----

    private String marshal(Report report) throws Exception {
        JAXBContext ctx = JAXBContext.newInstance(Report.class);
        Marshaller m = ctx.createMarshaller();
        m.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.TRUE);
        StringWriter sw = new StringWriter();
        m.marshal(report, sw);
        return sw.toString();
    }

    private Report unmarshal(String xml) throws Exception {
        JAXBContext ctx = JAXBContext.newInstance(Report.class);
        return (Report) ctx.createUnmarshaller().unmarshal(new StringReader(xml));
    }
}
