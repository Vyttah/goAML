package com.vyttah.goaml.engine;

import com.vyttah.goaml.domain.activity.Activity;
import com.vyttah.goaml.domain.activity.ReportParty;
import com.vyttah.goaml.domain.common.GoodsServices;
import com.vyttah.goaml.domain.common.ReportingPerson;
import com.vyttah.goaml.domain.enums.ReportCode;
import com.vyttah.goaml.domain.enums.SubmissionCode;
import com.vyttah.goaml.domain.party.TAddress;
import com.vyttah.goaml.domain.party.TPerson;
import com.vyttah.goaml.domain.party.TPersonIdentification;
import com.vyttah.goaml.domain.party.TPersonMyClient;
import com.vyttah.goaml.domain.party.TPhone;
import com.vyttah.goaml.domain.transaction.TFrom;
import com.vyttah.goaml.domain.transaction.TFromMyClient;
import com.vyttah.goaml.domain.transaction.TTo;
import com.vyttah.goaml.domain.transaction.Transaction;
import com.vyttah.goaml.engine.build.ReportHeader;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

/**
 * Test fixtures — one canonical sample per goAML report type. Used by both the engine
 * golden-file test and any downstream test that needs a realistic input shape.
 *
 * <p>The samples are intentionally minimal but cover the schema-mandatory fields per type,
 * including conditionals: {@code fiu_ref_number} for AIF/AIFT/ECDD/ECDDT, and
 * {@code location/reason/action} for STR/SAR.
 */
public final class SampleReports {

    private SampleReports() {}

    public record Sample(ReportHeader header, Activity activity, List<Transaction> transactions) {
        public boolean isActivity() { return activity != null; }
    }

    public static Sample sampleFor(ReportCode code) {
        return switch (code) {
            case DPMSR -> dpmsr();
            case SAR   -> sar();
            case AIF   -> aif();
            case ECDD  -> ecdd();
            case STR   -> str();
            case AIFT  -> aift();
            case ECDDT -> ecddt();
        };
    }

    // ---------- activity-based samples ----------

    private static Sample dpmsr() {
        ReportParty buyer = activityPersonParty(
                "Mohamad", "Ali Al-Jaber", "M", "784198012345678", "AE",
                "Cash purchase of gold bullion above AED 55,000 threshold");

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
        activity.setReportParties(List.of(buyer));
        activity.setGoodsServices(List.of(gold));

        return new Sample(
                header(ReportCode.DPMSR, "DPMSR-2026-001", null,
                        "DPMS cash transaction reporting threshold met",
                        "Customer due diligence completed; transaction filed",
                        List.of("DPMSR_CASH_THRESHOLD")),
                activity, null);
    }

    private static Sample sar() {
        ReportParty subject = activityPersonParty(
                "Aisha", "Mahmoud", "F", "784199012345672", "AE",
                "Unusual frequency of high-value transfers without commercial rationale");

        Activity activity = new Activity();
        activity.setReportParties(List.of(subject));

        return new Sample(
                header(ReportCode.SAR, "SAR-2026-014", null,
                        "Pattern of behavioural red flags identified",
                        "Account placed under enhanced monitoring; STR follow-up considered",
                        List.of("UNUSUAL_FREQUENCY")),
                activity, null);
    }

    private static Sample aif() {
        ReportParty subject = activityPersonParty(
                "Yusuf", "Rahman", "M", "784198012345699", "AE",
                "Additional information furnished in response to FIU enquiry");

        Activity activity = new Activity();
        activity.setReportParties(List.of(subject));

        return new Sample(
                header(ReportCode.AIF, "AIF-2026-008", "FIU-REQ-AIF-2026-008",
                        null, null,
                        List.of("FIU_REQUEST_RESPONSE")),
                activity, null);
    }

    private static Sample ecdd() {
        ReportParty subject = activityPersonParty(
                "Khalid", "Saleh", "M", "784199012345633", "AE",
                "Enhanced customer due diligence findings for high-risk PEP");

        Activity activity = new Activity();
        activity.setReportParties(List.of(subject));

        return new Sample(
                header(ReportCode.ECDD, "ECDD-2026-021", "FIU-REQ-ECDD-2026-021",
                        null, null,
                        List.of("PEP_REVIEW")),
                activity, null);
    }

    // ---------- transaction-based samples ----------

    private static Sample str() {
        return new Sample(
                header(ReportCode.STR, "STR-2026-0042", null,
                        "Customer conducted multiple cash deposits below threshold within 48 hours",
                        "Account flagged; subsequent activity under enhanced monitoring",
                        List.of("STRUCTURING_SUSPECTED")),
                null, List.of(bipartyCashTransfer(
                        "TXN-987654", "INT-2026-555",
                        new BigDecimal("120000.00"),
                        "Dubai Main Branch",
                        "International wire transfer following large cash deposit")));
    }

    private static Sample aift() {
        return new Sample(
                header(ReportCode.AIFT, "AIFT-2026-009", "FIU-REQ-AIFT-2026-009",
                        null, null,
                        List.of("FIU_REQUEST_RESPONSE")),
                null, List.of(bipartyCashTransfer(
                        "TXN-AIFT-001", "INT-2026-AIFT-001",
                        new BigDecimal("65000.00"),
                        "Abu Dhabi Branch",
                        "Cash deposit referenced in FIU request AIFT-2026-009")));
    }

    private static Sample ecddt() {
        return new Sample(
                header(ReportCode.ECDDT, "ECDDT-2026-022", "FIU-REQ-ECDDT-2026-022",
                        null, null,
                        List.of("PEP_REVIEW")),
                null, List.of(bipartyCashTransfer(
                        "TXN-ECDDT-001", "INT-2026-ECDDT-001",
                        new BigDecimal("220000.00"),
                        "Dubai Main Branch",
                        "Transaction reviewed as part of ECDD on high-risk PEP")));
    }

    // ---------- shared builders ----------

    private static ReportHeader header(ReportCode code, String entityRef, String fiuRef,
                                       String reason, String action, List<String> indicators) {
        return new ReportHeader(
                101, "DXB-MAIN",
                SubmissionCode.E, code,
                entityRef, fiuRef,
                odt("2026-05-26T10:00:00"),
                "AED",
                defaultMlro(),
                defaultLocation(),
                reason, action,
                indicators);
    }

    private static ReportingPerson defaultMlro() {
        TPhone phone = new TPhone();
        phone.setContactType("BU");
        phone.setCountryPrefix("971");
        phone.setNumber("44441234");

        TAddress address = new TAddress();
        address.setAddressType("BU");
        address.setAddress("Office 501, Compliance Tower");
        address.setCity("Dubai");
        address.setCountryCode("AE");
        address.setState("Dubai");

        ReportingPerson p = new ReportingPerson();
        p.setGender("F");
        p.setFirstName("Sara");
        p.setLastName("Khan");
        p.setNationality1("AE");
        p.setResidence("AE");
        p.setIdNumber("784199012345678");
        p.setEmail("mlro@alpha-jewellers.ae");
        p.setPhones(List.of(phone));
        p.setAddresses(List.of(address));
        return p;
    }

    private static TAddress defaultLocation() {
        TAddress a = new TAddress();
        a.setAddressType("BU");
        a.setAddress("Showroom, Dubai Gold Souk");
        a.setCity("Dubai");
        a.setCountryCode("AE");
        a.setState("Dubai");
        return a;
    }

    private static ReportParty activityPersonParty(String first, String last, String gender,
                                                   String emiratesId, String nationality,
                                                   String reason) {
        TPhone phone = new TPhone();
        phone.setContactType("PRIVT");
        phone.setCountryPrefix("971");
        phone.setNumber("501112233");

        TAddress address = new TAddress();
        address.setAddressType("PRIVT");
        address.setAddress("Villa 12, Al Wasl Road");
        address.setCity("Dubai");
        address.setCountryCode(nationality);

        TPersonIdentification eid = new TPersonIdentification();
        eid.setType("EID");
        eid.setNumber(emiratesId);
        eid.setIssueDate(odt("2020-01-15T00:00:00"));
        eid.setExpiryDate(odt("2030-01-14T00:00:00"));
        eid.setIssueCountry(nationality);

        TPersonMyClient subject = new TPersonMyClient();
        subject.setGender(gender);
        subject.setFirstName(first);
        subject.setLastName(last);
        subject.setNationality1(nationality);
        subject.setResidence(nationality);
        subject.setIdNumber(emiratesId);
        subject.setPhones(List.of(phone));
        subject.setAddresses(List.of(address));
        subject.setIdentifications(List.of(eid));

        ReportParty party = new ReportParty();
        party.setSignificance(8);
        party.setReason(reason);
        party.setPersonMyClient(subject);
        return party;
    }

    private static Transaction bipartyCashTransfer(String txNumber, String internalRef,
                                                   BigDecimal amount, String location,
                                                   String description) {
        TPersonMyClient sender = new TPersonMyClient();
        sender.setGender("F");
        sender.setFirstName("Layla");
        sender.setLastName("Hassan");
        sender.setNationality1("AE");
        sender.setResidence("AE");
        sender.setIdNumber("784198512345671");

        TFromMyClient from = new TFromMyClient();
        from.setFromFundsCode("CASH");
        from.setFromPerson(sender);
        from.setFromCountry("AE");

        TPerson receiver = new TPerson();
        receiver.setGender("M");
        receiver.setFirstName("John");
        receiver.setLastName("Smith");
        receiver.setNationality1("GB");
        receiver.setResidence("GB");

        TTo to = new TTo();
        to.setToFundsCode("BANKD");
        to.setToPerson(receiver);
        to.setToCountry("GB");

        Transaction tx = new Transaction();
        tx.setTransactionNumber(txNumber);
        tx.setInternalRefNumber(internalRef);
        tx.setTransactionLocation(location);
        tx.setTransactionDescription(description);
        tx.setDateTransaction(odt("2026-05-24T14:30:00"));
        tx.setTransmodeCode("CASH");
        tx.setAmountLocal(amount);
        tx.setTFromMyClient(from);
        tx.setTTo(to);
        return tx;
    }

    private static OffsetDateTime odt(String isoLocal) {
        return OffsetDateTime.parse(isoLocal + "Z").withOffsetSameInstant(ZoneOffset.UTC);
    }
}
