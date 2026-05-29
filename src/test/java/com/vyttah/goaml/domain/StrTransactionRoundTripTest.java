package com.vyttah.goaml.domain;

import com.vyttah.goaml.domain.common.ReportIndicator;
import com.vyttah.goaml.domain.common.ReportingPerson;
import com.vyttah.goaml.domain.enums.ReportCode;
import com.vyttah.goaml.domain.enums.SubmissionCode;
import com.vyttah.goaml.domain.party.TAddress;
import com.vyttah.goaml.domain.party.TPerson;
import com.vyttah.goaml.domain.party.TPersonMyClient;
import com.vyttah.goaml.domain.transaction.TFrom;
import com.vyttah.goaml.domain.transaction.TFromMyClient;
import com.vyttah.goaml.domain.transaction.TTo;
import com.vyttah.goaml.domain.transaction.Transaction;
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
 * Phase 3 verification — round-trips a representative STR (transaction-based) report
 * through JAXB marshal + unmarshal, exercising the bi-party transaction shape (a client
 * conducting a cash deposit to an external recipient).
 */
class StrTransactionRoundTripTest {

    @Test
    void strBipartyTransactionRoundTripsThroughJaxb() throws Exception {
        Report report = buildSampleStr();

        String xml = marshal(report);

        assertThat(xml)
                .contains("<report>")
                .contains("<report_code>STR</report_code>")
                .contains("<transaction>")
                .contains("<transactionnumber>TXN-987654</transactionnumber>")
                .contains("<amount_local>120000.00</amount_local>")
                .contains("<t_from_my_client>")
                .contains("<t_to>");

        // Transaction-shape ordering: transactionnumber → amount_local → t_from_my_client → t_to.
        int txNumIdx          = xml.indexOf("<transactionnumber>");
        int amountIdx         = xml.indexOf("<amount_local>");
        int fromMyClientIdx   = xml.indexOf("<t_from_my_client>");
        int toIdx             = xml.indexOf("<t_to>");
        assertThat(txNumIdx).isPositive();
        assertThat(amountIdx).isGreaterThan(txNumIdx);
        assertThat(fromMyClientIdx).isGreaterThan(amountIdx);
        assertThat(toIdx).isGreaterThan(fromMyClientIdx);

        // Bi-party invariants: no <t_from> or <t_to_my_client>.
        assertThat(xml).doesNotContain("<t_from>");
        assertThat(xml).doesNotContain("<t_to_my_client>");

        // Round-trip
        Report parsed = unmarshal(xml);
        assertThat(parsed.getReportCode()).isEqualTo(ReportCode.STR);
        assertThat(parsed.getTransactions()).hasSize(1);
        Transaction tx = parsed.getTransactions().get(0);
        assertThat(tx.getTransactionNumber()).isEqualTo("TXN-987654");
        assertThat(tx.getAmountLocal()).isEqualByComparingTo("120000.00");
        assertThat(tx.getTransmodeCode()).isEqualTo("CASH");

        assertThat(tx.getTFromMyClient()).isNotNull();
        assertThat(tx.getTFromMyClient().getFromPerson()).isNotNull();
        assertThat(tx.getTFromMyClient().getFromPerson().getFirstName()).isEqualTo("Layla");
        assertThat(tx.getTFromMyClient().getFromCountry()).isEqualTo("AE");

        assertThat(tx.getTTo()).isNotNull();
        assertThat(tx.getTTo().getToPerson()).isNotNull();
        assertThat(tx.getTTo().getToPerson().getFirstName()).isEqualTo("John");
        assertThat(tx.getTTo().getToCountry()).isEqualTo("GB");
    }

    // ----- sample builder -----

    private Report buildSampleStr() {
        TAddress fromAddr = new TAddress();
        fromAddr.setAddressType("PRIVT");
        fromAddr.setAddress("Apartment 7B, Marina Tower");
        fromAddr.setCity("Dubai");
        fromAddr.setCountryCode("AE");

        TPersonMyClient sender = new TPersonMyClient();
        sender.setGender("F");
        sender.setFirstName("Layla");
        sender.setLastName("Hassan");
        sender.setNationality1("AE");
        sender.setResidence("AE");
        sender.setIdNumber("784198512345671");
        sender.setAddresses(List.of(fromAddr));

        TFromMyClient from = new TFromMyClient();
        from.setFromFundsCode("CASH");
        from.setFromPerson(sender);
        from.setFromCountry("AE");

        TAddress toAddr = new TAddress();
        toAddr.setAddressType("PRIVT");
        toAddr.setAddress("221B Baker Street");
        toAddr.setCity("London");
        toAddr.setCountryCode("GB");

        TPerson receiver = new TPerson();
        receiver.setGender("M");
        receiver.setFirstName("John");
        receiver.setLastName("Smith");
        receiver.setNationality1("GB");
        receiver.setResidence("GB");
        receiver.setAddresses(List.of(toAddr));

        TTo to = new TTo();
        to.setToFundsCode("BANKD");
        to.setToPerson(receiver);
        to.setToCountry("GB");

        Transaction tx = new Transaction();
        tx.setTransactionNumber("TXN-987654");
        tx.setInternalRefNumber("INT-2026-555");
        tx.setTransactionLocation("Dubai Main Branch");
        tx.setTransactionDescription("International wire transfer following large cash deposit");
        tx.setDateTransaction(odt("2026-05-24T14:30:00"));
        tx.setTransmodeCode("CASH");
        tx.setTransmodeComment("Cash conducted at counter, structured pattern");
        tx.setAmountLocal(new BigDecimal("120000.00"));
        tx.setTFromMyClient(from);
        tx.setTTo(to);

        ReportingPerson mlro = new ReportingPerson();
        mlro.setGender("F");
        mlro.setFirstName("Sara");
        mlro.setLastName("Khan");
        mlro.setNationality1("AE");
        mlro.setResidence("AE");
        mlro.setIdNumber("784199012345678");
        mlro.setEmail("mlro@example-bank.ae");

        TAddress location = new TAddress();
        location.setAddressType("BU");
        location.setAddress("Floor 12, Compliance Office, Example Bank HQ");
        location.setCity("Dubai");
        location.setCountryCode("AE");

        ReportIndicator indicator = new ReportIndicator("STRUCTURING_SUSPECTED");

        Report report = new Report();
        report.setRentityId(202);
        report.setSubmissionCode(SubmissionCode.E);
        report.setReportCode(ReportCode.STR);
        report.setEntityReference("STR-2026-0042");
        report.setSubmissionDate(odt("2026-05-26T09:00:00"));
        report.setCurrencyCodeLocal("AED");
        report.setReportingPerson(mlro);
        report.setLocation(location);
        report.setReason("Customer conducted multiple cash deposits below threshold within 48 hours");
        report.setAction("Account flagged; subsequent activity under enhanced monitoring");
        report.setTransactions(new ArrayList<>(List.of(tx)));
        report.setReportIndicators(new ArrayList<>(List.of(indicator)));
        return report;
    }

    private static OffsetDateTime odt(String isoLocal) {
        return OffsetDateTime.parse(isoLocal + "Z").withOffsetSameInstant(ZoneOffset.UTC);
    }

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
