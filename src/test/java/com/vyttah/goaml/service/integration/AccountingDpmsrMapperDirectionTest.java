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
 * Completes the direction → counterparty-role matrix for {@code AccountingDpmsrMapper}: the existing test
 * covers SALE→corporate→Buyer and PURCHASE→individual→Seller; these two cover the other corners (SALE makes
 * the counterparty the Buyer; PURCHASE makes them the Seller — independent of corporate vs individual), so a
 * regression in the role mapping for either party shape is caught.
 */
class AccountingDpmsrMapperDirectionTest {

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
    void individualPartyMapsToBuyerRoleOnSale() {
        AccountingTxnPayload.Party ind = new AccountingTxnPayload.Party(
                "INDIVIDUAL", "Jane Smith", null, null, null, null, null,
                new AccountingTxnPayload.Individual("Jane", "Smith", LocalDate.of(1990, 1, 1), "IN",
                        List.of(new AccountingTxnPayload.Identification("PASSPORT", "P999", null))));

        DpmsrCreateRequest r = AccountingDpmsrMapper.toCreateRequest(payload("SALE", ind), "ACC-777-SAL-9",
                NOW, "Mlro", "One");

        DpmsrCreateRequest.Party p = r.parties().get(0);
        assertThat(p.reason()).isEqualTo("Buyer");
        assertThat(p.person().firstName()).isEqualTo("Jane");
        assertThat(p.entity()).isNull();
    }

    @Test
    void corporatePartyMapsToSellerRoleOnPurchase() {
        AccountingTxnPayload.Party corp = new AccountingTxnPayload.Party(
                "CORPORATE", "Acme Gold FZE", "TL-123", "AE", null, null, null, null);

        DpmsrCreateRequest r = AccountingDpmsrMapper.toCreateRequest(payload("PURCHASE", corp), "ACC-777-PUR-9",
                NOW, "Mlro", "One");

        DpmsrCreateRequest.Party p = r.parties().get(0);
        assertThat(p.reason()).isEqualTo("Seller");
        assertThat(p.entity().name()).isEqualTo("Acme Gold FZE");
        assertThat(p.person()).isNull();
    }
}
