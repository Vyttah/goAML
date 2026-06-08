package com.vyttah.goaml.model.dto.integration;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * The self-contained payload the Vyttah accounting service POSTs to goAML for DPMS reportability (Phase 1.5b,
 * Model 2). Accounting assembles this from its transaction service + masters service (sale/purchase + cash
 * settlement + party + company) — goAML never calls back. Receiver-defined contract, shaped to accounting's
 * vocabulary; goAML maps it to a DPMSR, runs reportability, and (if reportable) creates a validated draft.
 *
 * @param companyId          accounting company id → resolves the goAML tenant (via {@code tenant_external_ref})
 * @param sourceDocument     the originating sale/purchase document (idempotency + direction)
 * @param transactionCurrency ISO currency of the deal (informational; the threshold is assessed in AED)
 * @param cashSettlement     the cash portion of the settlement — the basis for the AED 55,000 threshold
 * @param party              the counterparty (customer for a sale, supplier for a purchase)
 * @param goods              the precious-metals/stones line items (from the document's stock details)
 */
public record AccountingTxnPayload(
        @NotNull Integer companyId,
        @NotNull @Valid SourceDocument sourceDocument,
        String transactionCurrency,
        @NotNull @Valid CashSettlement cashSettlement,
        @NotNull @Valid Party party,
        @NotNull @Valid List<Goods> goods) {

    /** @param direction SALE (customer) | PURCHASE (supplier) */
    public record SourceDocument(
            @NotBlank String documentNumber,
            String documentType,
            LocalDate documentDate,
            @NotBlank String direction,
            String moduleType) {}

    /** The cash settlement (mode=CASH) accounting computed for this document, in the company base currency. */
    public record CashSettlement(
            @NotNull BigDecimal cashAmountBaseCurrency,
            String baseCurrency,
            LocalDate settlementDate,
            List<String> cashDocumentNumbers) {}

    /** @param category INDIVIDUAL | CORPORATE | OTHER */
    public record Party(
            @NotBlank String category,
            @NotBlank String name,
            String tradeLicenseNo,
            String countryOfIncorporation,
            LocalDate dateOfIncorporation,
            Address address,
            List<Contact> contacts,
            Individual individual) {}

    public record Address(String addressLine1, String city, String countryCode, String addressType) {}

    /** @param type MOBILE_NUMBER | PHONE_NUMBER | DIALING_NUMBER | EMAIL */
    public record Contact(String type, String value) {}

    public record Individual(
            String firstName,
            String lastName,
            LocalDate dateOfBirth,
            String nationalityCode,
            List<Identification> identifications) {}

    /** @param type e.g. EMIRATES_ID | PASSPORT | TRADE_LICENCE */
    public record Identification(String type, String number, LocalDate expiryDate) {}

    /** @param commodityType masters {@code CommodityDto.type}: METAL | COLOR_STONE | LOOSE_DIAMOND_* | DIAMOND_JEWELLERY_* | PEARL | WATCH */
    public record Goods(
            @NotBlank String commodityType,
            String commodityCode,
            String description,
            BigDecimal purity,
            BigDecimal grossWeight,
            BigDecimal netWeight,
            BigDecimal pureWeight,
            Integer pieces,
            @NotNull BigDecimal estimatedValue,
            String currencyCode,
            BigDecimal metalAmount,
            BigDecimal stoneAmount) {}
}
