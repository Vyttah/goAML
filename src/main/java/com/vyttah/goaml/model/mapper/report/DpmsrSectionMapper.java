package com.vyttah.goaml.model.mapper.report;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vyttah.goaml.domain.generated.ReportPartyType;
import com.vyttah.goaml.domain.generated.TAddress;
import com.vyttah.goaml.domain.generated.TEntity;
import com.vyttah.goaml.domain.generated.TEntityMyClient;
import com.vyttah.goaml.domain.generated.TPerson;
import com.vyttah.goaml.domain.generated.TPersonMyClient;
import com.vyttah.goaml.domain.generated.TPersonRegistrationInReport;
import com.vyttah.goaml.domain.generated.TPhone;
import com.vyttah.goaml.domain.generated.TTransItem;
import com.vyttah.goaml.engine.build.DpmsrReportInput;
import com.vyttah.goaml.model.entity.report.AdditionalDetail;
import com.vyttah.goaml.model.entity.report.GoodsAndServices;
import com.vyttah.goaml.model.entity.report.InvolvedPartyLegal;
import com.vyttah.goaml.model.entity.report.InvolvedPartyNatural;
import com.vyttah.goaml.model.entity.report.ReportDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Maps the engine's {@link DpmsrReportInput} — the same generated-JAXB-typed object that
 * {@link com.vyttah.goaml.engine.build.DpmsrReportBuilder} turns into XML — onto the five DPMSR section
 * tables (see {@code .planning/migrate-json-storage-to-sql.md}). Deliberately built from
 * {@code DpmsrReportInput} rather than the request DTOs: both {@code create(DpmsrCreateRequest, ...)} and
 * {@code create(DpmsrReportPayload, ...)} converge on this one object before XML generation, so mapping
 * from it guarantees the section tables can never drift from what was actually built into the XML — one
 * mapping path, not two.
 *
 * <p>Repeatable/nested sub-fields that don't get their own column (phones beyond the primary one, full
 * address lists, emails, director IDs, sanctions, related persons/entities, network devices, URLs,
 * entity identifications) are not split into per-category rows. Instead the whole source JAXB object
 * ({@link TEntity}/{@link TEntityMyClient}/{@link TPerson}/{@link TPersonMyClient}) is preserved verbatim
 * as one {@code additional_details} row per party — simpler than a category per sub-list, and it cannot
 * silently drop a field the way an exhaustive per-category mapping could.
 *
 * <p>{@code account}/{@code accountMyClient} parties are out of the 5-table budget (see §6a Q5 in the
 * design doc): the whole party (common fields + account object) is stored as one
 * {@code additional_details} row, {@code category = ACCOUNT_PARTY}, at the report level.
 */
@Component
@RequiredArgsConstructor
public class DpmsrSectionMapper {

    private static final String CAT_ENTITY_DETAIL = "ENTITY_DETAIL";
    private static final String CAT_PERSON_DETAIL = "PERSON_DETAIL";
    private static final String CAT_ACCOUNT_PARTY = "ACCOUNT_PARTY";

    private final ObjectMapper objectMapper;

    public DpmsrSectionResult toSections(DpmsrReportInput in, UUID reportId) {
        OffsetDateTime now = OffsetDateTime.now();

        ReportDetails details = reportDetails(in, reportId);

        List<GoodsAndServices> goods = new ArrayList<>();
        int gi = 0;
        for (TTransItem item : in.goods()) {
            goods.add(goodsRow(item, reportId, gi));
            gi++;
        }

        List<InvolvedPartyNatural> naturalParties = new ArrayList<>();
        List<InvolvedPartyLegal> legalParties = new ArrayList<>();
        List<AdditionalDetail> additionalDetails = new ArrayList<>();
        int pi = 0;
        for (ReportPartyType party : in.parties()) {
            mapParty(party, reportId, pi, naturalParties, legalParties, additionalDetails, now);
            pi++;
        }

        return new DpmsrSectionResult(details, goods, naturalParties, legalParties, additionalDetails);
    }

    // ---------- report_details ----------

    private ReportDetails reportDetails(DpmsrReportInput in, UUID reportId) {
        ReportDetails.ReportDetailsBuilder b = ReportDetails.builder()
                .reportId(reportId)
                .action(in.action())
                .reason(in.reason())
                .fiuRefNumber(in.fiuRefNumber())
                .rentityBranch(in.rentityBranch())
                .submissionDate(in.submissionDate());

        if (in.indicators() != null && !in.indicators().isEmpty()) {
            b.indicators(in.indicators().toArray(String[]::new));
        }

        TAddress loc = in.location();
        if (loc != null) {
            b.locationAddress(loc.getAddress())
                    .locationHouseNumber(loc.getHouseNumber())
                    .locationApartmentNumber(loc.getApartmentNumber())
                    .locationAdditionalLine1(loc.getAdditionalAddressLine1())
                    .locationAdditionalLine2(loc.getAdditionalAddressLine2())
                    .locationTown(loc.getTown())
                    .locationCity(loc.getCity())
                    .locationState(loc.getState())
                    .locationZip(loc.getZip())
                    .locationCountryCode(loc.getCountryCode())
                    .locationAddressType(loc.getAddressType())
                    .locationComments(loc.getComments());
        }

        TPersonRegistrationInReport rp = in.reportingPerson();
        if (rp != null) {
            b.reportingPersonFirstName(rp.getFirstName())
                    .reportingPersonMiddleName(rp.getMiddleName())
                    .reportingPersonLastName(rp.getLastName())
                    .reportingPersonGender(rp.getGender())
                    .reportingPersonTitle(rp.getTitle())
                    .reportingPersonPrefix(rp.getPrefix())
                    .reportingPersonBirthdate(rp.getBirthdate())
                    .reportingPersonBirthPlace(rp.getBirthPlace())
                    .reportingPersonMothersName(rp.getMothersName())
                    .reportingPersonAlias(rp.getAlias())
                    .reportingPersonSsn(rp.getSsn())
                    .reportingPersonIdNumber(rp.getIdNumber())
                    .reportingPersonPassportNumber(rp.getPassportNumber())
                    .reportingPersonPassportCountry(rp.getPassportCountry())
                    .reportingPersonNationality1(rp.getNationality1())
                    .reportingPersonNationality2(rp.getNationality2())
                    .reportingPersonNationality3(rp.getNationality3())
                    .reportingPersonResidence(rp.getResidence())
                    .reportingPersonOccupation(rp.getOccupation())
                    .reportingPersonEmployerName(rp.getEmployerName())
                    .reportingPersonTaxNumber(rp.getTaxNumber())
                    .reportingPersonTaxRegNumber(rp.getTaxRegNumber())
                    .reportingPersonSourceOfWealth(rp.getSourceOfWealth())
                    .reportingPersonDeceased(rp.isDeceased())
                    .reportingPersonDateDeceased(rp.getDateDeceased())
                    .reportingPersonComments(rp.getComments());
        }

        return b.build();
    }

    // ---------- goods_and_services ----------

    private GoodsAndServices goodsRow(TTransItem item, UUID reportId, int ordinal) {
        GoodsAndServices.GoodsAndServicesBuilder b = GoodsAndServices.builder()
                .id(UUID.randomUUID())
                .reportId(reportId)
                .ordinal(ordinal)
                .itemType(item.getItemType())
                .itemMake(item.getItemMake())
                .description(item.getDescription())
                .previouslyRegisteredTo(item.getPreviouslyRegisteredTo())
                .presentlyRegisteredTo(item.getPresentlyRegisteredTo())
                .estimatedValue(item.getEstimatedValue())
                .statusCode(item.getStatusCode())
                .statusComments(item.getStatusComments())
                .disposedValue(item.getDisposedValue())
                .currencyCode(item.getCurrencyCode() != null ? item.getCurrencyCode().value() : null)
                .size(item.getSize())
                .sizeUom(item.getSizeUom())
                .registrationDate(item.getRegistrationDate())
                .registrationNumber(item.getRegistrationNumber())
                .identificationNumber(item.getIdentificationNumber())
                .comments(item.getComments());

        TAddress addr = item.getAddress();
        if (addr != null) {
            b.addressLine(addr.getAddress())
                    .addressCity(addr.getCity())
                    .addressState(addr.getState())
                    .addressCountryCode(addr.getCountryCode())
                    .addressZip(addr.getZip())
                    .addressType(addr.getAddressType());
        }
        return b.build();
    }

    // ---------- parties (six-way choice) ----------

    private void mapParty(ReportPartyType party, UUID reportId, int ordinal,
                           List<InvolvedPartyNatural> naturalParties, List<InvolvedPartyLegal> legalParties,
                           List<AdditionalDetail> additionalDetails, OffsetDateTime now) {
        Short significance = party.getSignificance() != null ? party.getSignificance().shortValue() : null;

        if (party.getEntity() != null) {
            UUID id = UUID.randomUUID();
            legalParties.add(legalRow(id, reportId, ordinal, false, party, significance,
                    party.getEntity().getName(), party.getEntity().getCommercialName(),
                    party.getEntity().getIncorporationLegalForm(), party.getEntity().getIncorporationNumber(),
                    party.getEntity().getBusiness(), party.getEntity().getEntityStatus(),
                    party.getEntity().getEntityStatusDate(), party.getEntity().getIncorporationState(),
                    party.getEntity().getIncorporationCountryCode(), party.getEntity().getIncorporationDate(),
                    party.getEntity().isBusinessClosed(), party.getEntity().getDateBusinessClosed(),
                    party.getEntity().getTaxNumber(), party.getEntity().getTaxRegNumber(),
                    firstPhone(party.getEntity().getPhones() != null ? party.getEntity().getPhones().getPhone() : null),
                    firstAddress(party.getEntity().getAddresses() != null
                            ? party.getEntity().getAddresses().getAddress() : null)));
            addDetail(additionalDetails, reportId, null, null, id, CAT_ENTITY_DETAIL, party.getEntity(), now);
        } else if (party.getEntityMyClient() != null) {
            TEntityMyClient e = party.getEntityMyClient();
            UUID id = UUID.randomUUID();
            legalParties.add(legalRow(id, reportId, ordinal, true, party, significance,
                    e.getName(), e.getCommercialName(), e.getIncorporationLegalForm(), e.getIncorporationNumber(),
                    e.getBusiness(), e.getEntityStatus(), e.getEntityStatusDate(), e.getIncorporationState(),
                    e.getIncorporationCountryCode(), e.getIncorporationDate(), e.isBusinessClosed(),
                    e.getDateBusinessClosed(), e.getTaxNumber(), e.getTaxRegNumber(),
                    firstPhone(e.getPhones() != null ? e.getPhones().getPhone() : null),
                    firstAddress(e.getAddresses() != null ? e.getAddresses().getAddress() : null)));
            addDetail(additionalDetails, reportId, null, null, id, CAT_ENTITY_DETAIL, e, now);
        } else if (party.getPerson() != null) {
            TPerson p = party.getPerson();
            UUID id = UUID.randomUUID();
            naturalParties.add(naturalRow(id, reportId, ordinal, false, party, significance,
                    p.getFirstName(), p.getMiddleName(), p.getLastName(), p.getGender(), p.getTitle(),
                    p.getPrefix(), p.getBirthdate(), p.getBirthPlace(), p.getMothersName(), p.getAlias(),
                    p.getSsn(), p.getIdNumber(), p.getPassportNumber(), p.getPassportCountry(),
                    p.getNationality1(), p.getNationality2(), p.getNationality3(), p.getResidence(),
                    p.getOccupation(), p.getEmployerName(), p.isDeceased(), p.getDateDeceased(),
                    p.getTaxNumber(), p.getTaxRegNumber(), p.getSourceOfWealth(),
                    firstPhone(p.getPhones() != null ? p.getPhones().getPhone() : null),
                    firstAddress(p.getAddresses() != null ? p.getAddresses().getAddress() : null)));
            addDetail(additionalDetails, reportId, null, id, null, CAT_PERSON_DETAIL, p, now);
        } else if (party.getPersonMyClient() != null) {
            TPersonMyClient p = party.getPersonMyClient();
            UUID id = UUID.randomUUID();
            naturalParties.add(naturalRow(id, reportId, ordinal, true, party, significance,
                    p.getFirstName(), p.getMiddleName(), p.getLastName(), p.getGender(), p.getTitle(),
                    p.getPrefix(), p.getBirthdate(), p.getBirthPlace(), p.getMothersName(), p.getAlias(),
                    p.getSsn(), p.getIdNumber(), p.getPassportNumber(), p.getPassportCountry(),
                    p.getNationality1(), p.getNationality2(), p.getNationality3(), p.getResidence(),
                    p.getOccupation(), p.getEmployerName(), p.isDeceased(), p.getDateDeceased(),
                    p.getTaxNumber(), p.getTaxRegNumber(), p.getSourceOfWealth(),
                    firstPhone(p.getPhones() != null ? p.getPhones().getPhone() : null),
                    firstAddress(p.getAddresses() != null ? p.getAddresses().getAddress() : null)));
            addDetail(additionalDetails, reportId, null, id, null, CAT_PERSON_DETAIL, p, now);
        } else if (party.getAccount() != null) {
            addDetail(additionalDetails, reportId, null, null, null, CAT_ACCOUNT_PARTY,
                    accountPartyPayload(party, ordinal, false, party.getAccount()), now);
        } else if (party.getAccountMyClient() != null) {
            addDetail(additionalDetails, reportId, null, null, null, CAT_ACCOUNT_PARTY,
                    accountPartyPayload(party, ordinal, true, party.getAccountMyClient()), now);
        }
        // else: no subject set at all (an incomplete draft party) — no row, nothing to store beyond input.
    }

    @SuppressWarnings("java:S107")
    private InvolvedPartyLegal legalRow(UUID id, UUID reportId, int ordinal, boolean isMyClient,
            ReportPartyType party, Short significance, String name, String commercialName,
            String incorporationLegalForm, String incorporationNumber, String business, String entityStatus,
            OffsetDateTime entityStatusDate, String incorporationState, String incorporationCountryCode,
            OffsetDateTime incorporationDate, Boolean businessClosed, OffsetDateTime dateBusinessClosed,
            String taxNumber, String taxRegNumber, TPhone primaryPhone, TAddress primaryAddress) {
        return InvolvedPartyLegal.builder()
                .id(id).reportId(reportId).partyOrdinal(ordinal).isMyClient(isMyClient)
                .role(party.getRole()).reason(party.getReason()).country(party.getCountry())
                .comments(party.getComments()).isSuspected(party.isIsSuspected()).significance(significance)
                .name(name).commercialName(commercialName).incorporationLegalForm(incorporationLegalForm)
                .incorporationNumber(incorporationNumber).business(business).entityStatus(entityStatus)
                .entityStatusDate(entityStatusDate).incorporationState(incorporationState)
                .incorporationCountryCode(incorporationCountryCode).incorporationDate(incorporationDate)
                .businessClosed(businessClosed).dateBusinessClosed(dateBusinessClosed).taxNumber(taxNumber)
                .taxRegNumber(taxRegNumber)
                .primaryPhoneNumber(primaryPhone != null ? primaryPhone.getTphNumber() : null)
                .primaryAddressLine(primaryAddress != null ? primaryAddress.getAddress() : null)
                .primaryAddressCity(primaryAddress != null ? primaryAddress.getCity() : null)
                .primaryAddressCountryCode(primaryAddress != null ? primaryAddress.getCountryCode() : null)
                .build();
    }

    @SuppressWarnings("java:S107")
    private InvolvedPartyNatural naturalRow(UUID id, UUID reportId, int ordinal, boolean isMyClient,
            ReportPartyType party, Short significance, String firstName, String middleName, String lastName,
            String gender, String title, String prefix, OffsetDateTime birthdate, String birthPlace,
            String mothersName, String alias, String ssn, String idNumber, String passportNumber,
            String passportCountry, String nationality1, String nationality2, String nationality3,
            String residence, String occupation, String employerName, Boolean deceased,
            OffsetDateTime dateDeceased, String taxNumber, String taxRegNumber, String sourceOfWealth,
            TPhone primaryPhone, TAddress primaryAddress) {
        return InvolvedPartyNatural.builder()
                .id(id).reportId(reportId).partyOrdinal(ordinal).isMyClient(isMyClient)
                .role(party.getRole()).reason(party.getReason()).country(party.getCountry())
                .comments(party.getComments()).isSuspected(party.isIsSuspected()).significance(significance)
                .firstName(firstName).middleName(middleName).lastName(lastName).gender(gender).title(title)
                .prefix(prefix).birthdate(birthdate).birthPlace(birthPlace).mothersName(mothersName)
                .alias(alias).ssn(ssn).idNumber(idNumber).passportNumber(passportNumber)
                .passportCountry(passportCountry).nationality1(nationality1).nationality2(nationality2)
                .nationality3(nationality3).residence(residence).occupation(occupation)
                .employerName(employerName).deceased(deceased).dateDeceased(dateDeceased)
                .taxNumber(taxNumber).taxRegNumber(taxRegNumber).sourceOfWealth(sourceOfWealth)
                .primaryPhoneNumber(primaryPhone != null ? primaryPhone.getTphNumber() : null)
                .primaryAddressLine(primaryAddress != null ? primaryAddress.getAddress() : null)
                .primaryAddressCity(primaryAddress != null ? primaryAddress.getCity() : null)
                .primaryAddressCountryCode(primaryAddress != null ? primaryAddress.getCountryCode() : null)
                .build();
    }

    private Object accountPartyPayload(ReportPartyType party, int ordinal, boolean isMyClient, Object account) {
        return new AccountPartyPayload(ordinal, isMyClient, party.getRole(), party.getReason(),
                party.getCountry(), party.getComments(), party.isIsSuspected(), party.getSignificance(),
                account);
    }

    private record AccountPartyPayload(int partyOrdinal, boolean isMyClient, String role, String reason,
                                        String country, String comments, Boolean isSuspected,
                                        Integer significance, Object account) {
    }

    private static TPhone firstPhone(List<TPhone> phones) {
        return phones != null && !phones.isEmpty() ? phones.get(0) : null;
    }

    private static TAddress firstAddress(List<TAddress> addresses) {
        return addresses != null && !addresses.isEmpty() ? addresses.get(0) : null;
    }

    private void addDetail(List<AdditionalDetail> sink, UUID reportId, UUID goodsId, UUID naturalPartyId,
                            UUID legalPartyId, String category, Object payload, OffsetDateTime now) {
        sink.add(AdditionalDetail.builder()
                .id(UUID.randomUUID())
                .reportId(reportId)
                .goodsId(goodsId)
                .naturalPartyId(naturalPartyId)
                .legalPartyId(legalPartyId)
                .category(category)
                .data(toJson(payload))
                .createdAt(now)
                .build());
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize DPMSR section overflow JSON", e);
        }
    }
}
