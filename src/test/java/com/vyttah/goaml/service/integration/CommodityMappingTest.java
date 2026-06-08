package com.vyttah.goaml.service.integration;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 1.5b.4 — accounting commodity classification → DPMS precious-ness + goAML item_type codes.
 */
class CommodityMappingTest {

    @Test
    void metalsStonesDiamondsPearlsArePrecious() {
        assertThat(CommodityMapping.isPrecious("METAL", null, null)).isTrue();
        assertThat(CommodityMapping.isPrecious("COLOR_STONE", null, null)).isTrue();
        assertThat(CommodityMapping.isPrecious("LOOSE_DIAMOND_NATURAL", null, null)).isTrue();
        assertThat(CommodityMapping.isPrecious("DIAMOND_JEWELLERY_LAB_GROWN", null, null)).isTrue();
        assertThat(CommodityMapping.isPrecious("PEARL", null, null)).isTrue();
    }

    @Test
    void watchIsPreciousOnlyWithMetalOrStoneValue() {
        assertThat(CommodityMapping.isPrecious("WATCH", BigDecimal.ZERO, BigDecimal.ZERO)).isFalse();
        assertThat(CommodityMapping.isPrecious("WATCH", new BigDecimal("100"), null)).isTrue();
        assertThat(CommodityMapping.isPrecious("WATCH", null, new BigDecimal("5"))).isTrue();
    }

    @Test
    void unknownOrNullIsNotPrecious() {
        assertThat(CommodityMapping.isPrecious("FURNITURE", new BigDecimal("1"), null)).isFalse();
        assertThat(CommodityMapping.isPrecious(null, null, null)).isFalse();
    }

    @Test
    void mapsToValidXsdItemTypeCodes() {
        assertThat(CommodityMapping.toItemType("METAL")).isEqualTo("GOLD");
        assertThat(CommodityMapping.toItemType("COLOR_STONE")).isEqualTo("GEM");
        assertThat(CommodityMapping.toItemType("PEARL")).isEqualTo("GEM");
        assertThat(CommodityMapping.toItemType("LOOSE_DIAMOND_NATURAL")).isEqualTo("DIMND");
        assertThat(CommodityMapping.toItemType("DIAMOND_JEWELLERY_NATURAL")).isEqualTo("JEWEL");
        assertThat(CommodityMapping.toItemType("WATCH")).isEqualTo("WATCH");
        assertThat(CommodityMapping.toItemType(null)).isEqualTo("GOLD");
        assertThat(CommodityMapping.toItemType("SOMETHING_ELSE")).isEqualTo("GOLD");
    }
}
