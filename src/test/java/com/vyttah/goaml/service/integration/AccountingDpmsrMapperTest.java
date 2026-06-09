package com.vyttah.goaml.service.integration;

import com.vyttah.goaml.model.dto.integration.AccountingTxnPayload;
import com.vyttah.goaml.model.dto.report.DpmsrCreateRequest;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 1.5b.4 — accounting payload → DPMSR mapping (corporate entity vs individual person, direction → role, goods).
 */
class AccountingDpmsrMapperTest {

    private static final OffsetDateTime NOW = OffsetDateTime.of(2026, 6, 8, 0, 0, 0, 0, ZoneOffset.UTC);

    private static AccountingTxnPayload payload(String direction, AccountingTxnPayload.Party party) {
        return new AccountingTxnPayload(777,
                new AccountingTxnPayload.SourceDocument("SAL-1", "SAL", LocalDate.now(), direction, "BULLION"),
                "AED",
                new AccountingTxnPayload.CashSettlement(new BigDecimal("90000"), "AED", LocalDate.now(),
                        List.of("REC-1")),
                party,
                List.of(new AccountingTxnPayload.Goods("METAL", "GLD", "22K gold bar", null, null, null, null,
                        null, new BigDecimal("90000"), "AED", new BigDecimal("90000"), null)));
    }

    @Test
    void corporatePartyMapsToEntityWithBuyerRoleOnSale() {
        AccountingTxnPayload.Party corp = new AccountingTxnPayload.Party(
                "CORPORATE", "Acme Gold FZE", "TL-123", "AE", null, null, null, null);

        DpmsrCreateRequest r = AccountingDpmsrMapper.toCreateRequest(payload("SALE", corp), "ACC-777-SAL-1",
                NOW, "Mlro", "One");

        assertThat(r.entityReference()).isEqualTo("ACC-777-SAL-1");
        assertThat(r.indicators()).contains("DPMSJ");
        assertThat(r.reportingPerson().firstName()).isEqualTo("Mlro");
        DpmsrCreateRequest.Party p = r.parties().get(0);
        assertThat(p.reason()).isEqualTo("Buyer");
        assertThat(p.entity().name()).isEqualTo("Acme Gold FZE");
        assertThat(p.entity().incorporationNumber()).isEqualTo("TL-123");
        assertThat(p.entity().incorporationCountryCode()).isEqualTo("AE");
        assertThat(r.goods().get(0).itemType()).isEqualTo("GOLD");
        assertThat(r.goods().get(0).estimatedValue()).isEqualByComparingTo("90000");
        // the invoice (source document) number becomes the good's registration_number
        assertThat(r.goods().get(0).registrationNumber()).isEqualTo("SAL-1");
    }

    @Test
    void individualPartyMapsToPersonWithSellerRoleOnPurchase() {
        AccountingTxnPayload.Party ind = new AccountingTxnPayload.Party(
                "INDIVIDUAL", "Fallback Name", null, null, null, null, null,
                new AccountingTxnPayload.Individual("John", "Doe", LocalDate.of(1990, 1, 1), "IN",
                        List.of(new AccountingTxnPayload.Identification("PASSPORT", "P123", null))));

        DpmsrCreateRequest r = AccountingDpmsrMapper.toCreateRequest(payload("PURCHASE", ind), "ACC-777-PUR-1",
                NOW, "Mlro", "One");

        DpmsrCreateRequest.Party p = r.parties().get(0);
        assertThat(p.reason()).isEqualTo("Seller");
        assertThat(p.entity()).isNull();
        assertThat(p.person().firstName()).isEqualTo("John");
        assertThat(p.person().lastName()).isEqualTo("Doe");
        assertThat(p.person().idNumber()).isEqualTo("P123");
        assertThat(p.person().nationality()).isEqualTo("IN");
    }

    @Test
    void individualWithoutNamesFallsBackToSplittingPartyName() {
        AccountingTxnPayload.Party ind = new AccountingTxnPayload.Party(
                "INDIVIDUAL", "Jane Smith", null, null, null, null, null, null);

        DpmsrCreateRequest r = AccountingDpmsrMapper.toCreateRequest(payload("SALE", ind), "ACC-777-SAL-2",
                NOW, "Mlro", "One");

        assertThat(r.parties().get(0).person().firstName()).isEqualTo("Jane");
        assertThat(r.parties().get(0).person().lastName()).isEqualTo("Smith");
    }
}
