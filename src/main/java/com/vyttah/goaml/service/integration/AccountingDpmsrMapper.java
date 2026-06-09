package com.vyttah.goaml.service.integration;

import com.vyttah.goaml.model.dto.integration.AccountingTxnPayload;
import com.vyttah.goaml.model.dto.report.DpmsrCreateRequest;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

/**
 * Maps an {@link AccountingTxnPayload} onto a {@link DpmsrCreateRequest} (Phase 1.5b, Model 2). Produces the
 * field set proven to validate (entity/person party, goods, reporting person, reason/action/indicator);
 * coded/optional fields that would need the (pending) FIU lookups are left unset rather than guessed.
 */
final class AccountingDpmsrMapper {

    private static final String DEFAULT_INDICATOR = "DPMSJ";
    private static final String REASON =
            "Auto-detected cash dealing in precious metals/stones — DPMS threshold met";
    private static final String ACTION = "Filed";

    private AccountingDpmsrMapper() {
    }

    static DpmsrCreateRequest toCreateRequest(AccountingTxnPayload p, String entityReference,
                                              OffsetDateTime submissionDate, String mlroFirst, String mlroLast) {
        DpmsrCreateRequest.Person reportingPerson = new DpmsrCreateRequest.Person(
                null, mlroFirst, mlroLast, null, null, null, null, null, null, null, null, null, null);

        return new DpmsrCreateRequest(
                null,
                entityReference,
                submissionDate,
                null,
                REASON,
                ACTION,
                List.of(DEFAULT_INDICATOR),
                reportingPerson,
                null,
                List.of(party(p)),
                goods(p));
    }

    private static DpmsrCreateRequest.Party party(AccountingTxnPayload p) {
        // SALE → the counterparty is the buyer; PURCHASE → the counterparty is the seller.
        String role = "SALE".equalsIgnoreCase(p.sourceDocument().direction()) ? "Buyer" : "Seller";
        AccountingTxnPayload.Party src = p.party();

        if ("INDIVIDUAL".equalsIgnoreCase(src.category())) {
            return new DpmsrCreateRequest.Party(role, null, null, person(src));
        }
        DpmsrCreateRequest.Entity entity = new DpmsrCreateRequest.Entity(
                src.name(), null, src.tradeLicenseNo(), null, src.countryOfIncorporation(), null, null);
        return new DpmsrCreateRequest.Party(role, null, entity, null);
    }

    private static DpmsrCreateRequest.Person person(AccountingTxnPayload.Party src) {
        AccountingTxnPayload.Individual ind = src.individual();
        String first;
        String last;
        if (ind != null && notBlank(ind.firstName())) {
            first = ind.firstName();
            last = notBlank(ind.lastName()) ? ind.lastName() : "Unknown";
        } else {
            String[] parts = src.name().trim().split("\\s+", 2);
            first = parts[0];
            last = parts.length > 1 ? parts[1] : "Unknown";
        }
        OffsetDateTime dob = ind != null ? toOffset(ind.dateOfBirth()) : null;
        String nationality = ind != null ? ind.nationalityCode() : null;
        String idNumber = ind != null && ind.identifications() != null && !ind.identifications().isEmpty()
                ? ind.identifications().get(0).number() : null;
        return new DpmsrCreateRequest.Person(
                null, first, last, dob, nationality, nationality, null, idNumber, null, null, null, null, null);
    }

    private static List<DpmsrCreateRequest.Goods> goods(AccountingTxnPayload p) {
        String fallbackCurrency = p.transactionCurrency() != null ? p.transactionCurrency() : "AED";
        // the source document number is the goAML good's registration_number (the invoice reference)
        String registrationNumber = p.sourceDocument() != null ? p.sourceDocument().documentNumber() : null;
        return p.goods().stream()
                .map(g -> new DpmsrCreateRequest.Goods(
                        CommodityMapping.toItemType(g.commodityType()),
                        null,
                        g.description() != null ? g.description() : g.commodityCode(),
                        null,
                        null,
                        g.estimatedValue(),
                        g.currencyCode() != null ? g.currencyCode() : fallbackCurrency,
                        null,
                        null,
                        null,
                        null,
                        null,
                        registrationNumber,
                        null))
                .toList();
    }

    private static OffsetDateTime toOffset(LocalDate date) {
        return date == null ? null : date.atStartOfDay().atOffset(ZoneOffset.UTC);
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}
