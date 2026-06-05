package com.vyttah.goaml.engine;

import com.vyttah.goaml.domain.generated.ActivityType;
import com.vyttah.goaml.domain.generated.CurrencyType;
import com.vyttah.goaml.domain.generated.FundsType;
import com.vyttah.goaml.domain.generated.Report;
import com.vyttah.goaml.domain.generated.ReportPartyType;
import com.vyttah.goaml.domain.generated.ReportType;
import com.vyttah.goaml.domain.generated.TAddress;
import com.vyttah.goaml.domain.generated.TPerson;
import com.vyttah.goaml.domain.generated.TPersonIdentification;
import com.vyttah.goaml.domain.generated.TPersonMyClient;
import com.vyttah.goaml.domain.generated.TPersonRegistrationInReport;
import com.vyttah.goaml.domain.generated.TPhone;
import com.vyttah.goaml.domain.generated.TTransItem;
import com.vyttah.goaml.engine.build.ReportHeader;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

/**
 * Test fixtures — one canonical sample per goAML report type, built against the xjc-generated model
 * (goAMLSchema.xsd 5.0.2). Used by the engine golden-file test and any downstream test that needs a
 * realistic input shape.
 *
 * <p>The samples are intentionally minimal but cover the schema-mandatory fields per type, including
 * conditionals: {@code fiu_ref_number} for AIF/AIFT/ECDD/ECDDT, and {@code location/reason/action} for
 * STR/SAR. The activity-shaped report's body is set on the report via {@code setReportActivity(...)}.
 */
public final class SampleReports {

    private SampleReports() {}

    public record Sample(ReportHeader header, ActivityType activity, List<Report.Transaction> transactions) {
        public boolean isActivity() { return activity != null; }
    }

    public static Sample sampleFor(ReportType code) {
        return switch (code) {
            case DPMSR -> dpmsr();
            case SAR   -> sar();
            case AIF   -> aif();
            case ECDD  -> ecdd();
            case STR   -> str();
            case AIFT  -> aift();
            case ECDDT -> ecddt();
            default    -> throw new IllegalArgumentException("No canonical sample for report code " + code);
        };
    }

    // ---------- activity-based samples ----------

    private static Sample dpmsr() {
        ReportPartyType buyer = activityPersonParty(
                "Mohamad", "Ali Al-Jaber", "M", "784198012345678", "AE",
                "Cash purchase of gold bullion above AED 55,000 threshold");

        TTransItem gold = new TTransItem();
        gold.setItemType("GOLD");
        gold.setItemMake("Emirates Gold DMCC");
        gold.setDescription("1kg gold bullion bar, .9999 fine");
        gold.setEstimatedValue(new BigDecimal("75000.00"));
        gold.setCurrencyCode(CurrencyType.AED);
        gold.setSize(new BigDecimal("1000"));
        gold.setSizeUom("GRAM");
        gold.setStatusCode("SOLD");
        gold.setPresentlyRegisteredTo("Mohamad Ali Al-Jaber");

        ActivityType activity = new ActivityType();
        activity.setReportParties(reportParties(buyer));
        activity.setGoodsServices(goodsServices(gold));

        return new Sample(
                header(ReportType.DPMSR, "DPMSR-2026-001", null,
                        "DPMS cash transaction reporting threshold met",
                        "Customer due diligence completed; transaction filed",
                        List.of("DPMSJ")),
                activity, null);
    }

    private static Sample sar() {
        ReportPartyType subject = activityPersonParty(
                "Aisha", "Mahmoud", "F", "784199012345672", "AE",
                "Unusual frequency of high-value transfers without commercial rationale");

        ActivityType activity = new ActivityType();
        activity.setReportParties(reportParties(subject));

        return new Sample(
                header(ReportType.SAR, "SAR-2026-014", null,
                        "Pattern of behavioural red flags identified",
                        "Account placed under enhanced monitoring; STR follow-up considered",
                        List.of("STRUC")),
                activity, null);
    }

    private static Sample aif() {
        ReportPartyType subject = activityPersonParty(
                "Yusuf", "Rahman", "M", "784198012345699", "AE",
                "Additional information furnished in response to FIU enquiry");

        ActivityType activity = new ActivityType();
        activity.setReportParties(reportParties(subject));

        return new Sample(
                header(ReportType.AIF, "AIF-2026-008", "FIU-REQ-AIF-2026-008",
                        null, null,
                        List.of("RFIUR")),
                activity, null);
    }

    private static Sample ecdd() {
        ReportPartyType subject = activityPersonParty(
                "Khalid", "Saleh", "M", "784199012345633", "AE",
                "Enhanced customer due diligence findings for high-risk PEP");

        ActivityType activity = new ActivityType();
        activity.setReportParties(reportParties(subject));

        return new Sample(
                header(ReportType.ECDD, "ECDD-2026-021", "FIU-REQ-ECDD-2026-021",
                        null, null,
                        List.of("STRUC")),
                activity, null);
    }

    // ---------- transaction-based samples ----------

    private static Sample str() {
        return new Sample(
                header(ReportType.STR, "STR-2026-0042", null,
                        "Customer conducted multiple cash deposits below threshold within 48 hours",
                        "Account flagged; subsequent activity under enhanced monitoring",
                        List.of("STRUC")),
                null, List.of(bipartyCashTransfer(
                        "TXN-987654", "INT-2026-555",
                        new BigDecimal("120000.00"),
                        "Dubai Main Branch",
                        "International wire transfer following large cash deposit")));
    }

    private static Sample aift() {
        return new Sample(
                header(ReportType.AIFT, "AIFT-2026-009", "FIU-REQ-AIFT-2026-009",
                        null, null,
                        List.of("RFIUR")),
                null, List.of(bipartyCashTransfer(
                        "TXN-AIFT-001", "INT-2026-AIFT-001",
                        new BigDecimal("65000.00"),
                        "Abu Dhabi Branch",
                        "Cash deposit referenced in FIU request AIFT-2026-009")));
    }

    private static Sample ecddt() {
        return new Sample(
                header(ReportType.ECDDT, "ECDDT-2026-022", "FIU-REQ-ECDDT-2026-022",
                        null, null,
                        List.of("STRUC")),
                null, List.of(bipartyCashTransfer(
                        "TXN-ECDDT-001", "INT-2026-ECDDT-001",
                        new BigDecimal("220000.00"),
                        "Dubai Main Branch",
                        "Transaction reviewed as part of ECDD on high-risk PEP")));
    }

    // ---------- shared builders ----------

    private static ReportHeader header(ReportType code, String entityRef, String fiuRef,
                                       String reason, String action, List<String> indicators) {
        return new ReportHeader(
                101, "DXB-MAIN",
                "E", code,
                entityRef, fiuRef,
                odt("2026-05-26T10:00:00"),
                CurrencyType.AED,
                defaultMlro(),
                defaultLocation(),
                reason, action,
                indicators);
    }

    private static TPersonRegistrationInReport defaultMlro() {
        TPersonRegistrationInReport p = new TPersonRegistrationInReport();
        p.setGender("F");
        p.setFirstName("Sara");
        p.setLastName("Khan");
        p.setNationality1("AE");
        p.setResidence("AE");
        p.setIdNumber("784199012345678");
        p.setOccupation("MLRO");

        TPersonRegistrationInReport.Phones phones = new TPersonRegistrationInReport.Phones();
        phones.getPhone().add(phone("BU", "L", "971", "44441234"));
        p.setPhones(phones);

        TPersonRegistrationInReport.Addresses addresses = new TPersonRegistrationInReport.Addresses();
        addresses.getAddress().add(address("BU", "Office 501, Compliance Tower", "Dubai", "AE", "Dubai"));
        p.setAddresses(addresses);
        return p;
    }

    private static TAddress defaultLocation() {
        return address("BU", "Showroom, Dubai Gold Souk", "Dubai", "AE", "Dubai");
    }

    private static ReportPartyType activityPersonParty(String first, String last, String gender,
                                                       String emiratesId, String nationality,
                                                       String reason) {
        TPersonMyClient subject = new TPersonMyClient();
        subject.setGender(gender);
        subject.setFirstName(first);
        subject.setLastName(last);
        subject.setBirthdate(odt("1985-03-12T00:00:00")); // mandatory in t_person_my_client
        subject.setNationality1(nationality);
        subject.setResidence(nationality);
        subject.setIdNumber(emiratesId);
        subject.setTaxRegNumber("Y"); // mandatory in t_person_my_client

        TPersonMyClient.Phones phones = new TPersonMyClient.Phones();
        phones.getPhone().add(phone("PRIVT", "L", "971", "501112233"));
        subject.setPhones(phones);

        TPersonMyClient.Addresses addresses = new TPersonMyClient.Addresses();
        addresses.getAddress().add(address("PRIVT", "Villa 12, Al Wasl Road", "Dubai", nationality, "Dubai"));
        subject.setAddresses(addresses);

        TPersonIdentification eid = new TPersonIdentification();
        eid.setType("EID");
        eid.setNumber(emiratesId);
        eid.setIssueDate(odt("2020-01-15T00:00:00"));
        eid.setExpiryDate(odt("2030-01-14T00:00:00"));
        eid.setIssueCountry(nationality);
        TPersonMyClient.Identifications ids = new TPersonMyClient.Identifications();
        ids.getIdentification().add(eid);
        subject.setIdentifications(ids);

        ReportPartyType party = new ReportPartyType();
        party.setSignificance(8);
        party.setReason(reason);
        party.setPersonMyClient(subject);
        return party;
    }

    private static Report.Transaction bipartyCashTransfer(String txNumber, String internalRef,
                                                          BigDecimal amount, String location,
                                                          String description) {
        TPersonMyClient sender = new TPersonMyClient();
        sender.setGender("F");
        sender.setFirstName("Layla");
        sender.setLastName("Hassan");
        sender.setBirthdate(odt("1989-07-02T00:00:00")); // mandatory in t_person_my_client
        sender.setNationality1("AE");
        sender.setResidence("AE");
        sender.setIdNumber("784198512345671");
        sender.setTaxRegNumber("Y"); // mandatory in t_person_my_client
        TPersonMyClient.Phones senderPhones = new TPersonMyClient.Phones();
        senderPhones.getPhone().add(phone("PRIVT", "L", "971", "509998877"));
        sender.setPhones(senderPhones); // phones wrapper mandatory in t_person_my_client
        TPersonIdentification senderEid = new TPersonIdentification();
        senderEid.setType("EID");
        senderEid.setNumber("784198512345671");
        senderEid.setIssueDate(odt("2020-01-15T00:00:00"));
        senderEid.setExpiryDate(odt("2030-01-14T00:00:00"));
        senderEid.setIssueCountry("AE");
        TPersonMyClient.Identifications senderIds = new TPersonMyClient.Identifications();
        senderIds.getIdentification().add(senderEid);
        sender.setIdentifications(senderIds); // identifications mandatory in t_person_my_client

        Report.Transaction.TFromMyClient from = new Report.Transaction.TFromMyClient();
        from.setFromFundsCode(FundsType.CASH);
        from.setFromPerson(sender);
        from.setFromCountry("AE");

        TPerson receiver = new TPerson();
        receiver.setGender("M");
        receiver.setFirstName("John");
        receiver.setLastName("Smith");
        receiver.setNationality1("GB");
        receiver.setResidence("GB");

        Report.Transaction.TTo to = new Report.Transaction.TTo();
        to.setToFundsCode(FundsType.BANKD);
        to.setToPerson(receiver);
        to.setToCountry("GB");

        Report.Transaction tx = new Report.Transaction();
        tx.setTransactionnumber(txNumber);
        tx.setInternalRefNumber(internalRef);
        tx.setTransactionLocation(location);
        tx.setTransactionDescription(description);
        tx.setDateTransaction(odt("2026-05-24T14:30:00"));
        tx.setTransmodeCode("ELCFT"); // conduction_type code, valid in both the lookup and the XSD enum
        tx.setTransmodeComment("E");  // mandatory single-char field after transmode_code
        tx.setAmountLocal(amount);
        tx.setTFromMyClient(from);
        tx.setTTo(to);
        return tx;
    }

    // ---------- wrapper helpers ----------

    private static ActivityType.ReportParties reportParties(ReportPartyType... parties) {
        ActivityType.ReportParties wrapper = new ActivityType.ReportParties();
        for (ReportPartyType p : parties) {
            wrapper.getReportParty().add(p);
        }
        return wrapper;
    }

    private static ActivityType.GoodsServices goodsServices(TTransItem... items) {
        ActivityType.GoodsServices wrapper = new ActivityType.GoodsServices();
        for (TTransItem i : items) {
            wrapper.getItem().add(i);
        }
        return wrapper;
    }

    private static TPhone phone(String contactType, String communicationType, String prefix, String number) {
        TPhone phone = new TPhone();
        phone.setTphContactType(contactType);
        phone.setTphCommunicationType(communicationType);
        phone.setTphCountryPrefix(prefix);
        phone.setTphNumber(number);
        return phone;
    }

    private static TAddress address(String type, String line, String city, String country, String state) {
        TAddress a = new TAddress();
        a.setAddressType(type);
        a.setAddress(line);
        a.setCity(city);
        a.setCountryCode(country);
        a.setState(state);
        return a;
    }

    private static OffsetDateTime odt(String isoLocal) {
        return OffsetDateTime.parse(isoLocal + "Z").withOffsetSameInstant(ZoneOffset.UTC);
    }
}
