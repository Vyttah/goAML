package com.vyttah.goaml.service.integration;

import java.math.BigDecimal;

/**
 * Maps the accounting/masters {@code CommodityDto.type} classification onto goAML DPMSR goods semantics
 * (Phase 1.5b): whether a line is a precious-metals/stones dealing (DPMS scope) and a goAML goods item-type
 * label. Kept in the integration layer so accounting vocabulary stays out of the engine.
 *
 * <p>The {@code WATCH} rule (confirmed with the product owner): a watch counts as DPMS goods only when the
 * line carries precious-metal or stone value.
 */
final class CommodityMapping {

    private CommodityMapping() {
    }

    static boolean isPrecious(String commodityType, BigDecimal metalAmount, BigDecimal stoneAmount) {
        if (commodityType == null) {
            return false;
        }
        return switch (commodityType) {
            case "METAL", "COLOR_STONE", "LOOSE_DIAMOND_NATURAL", "LOOSE_DIAMOND_LAB_GROWN",
                 "DIAMOND_JEWELLERY_NATURAL", "DIAMOND_JEWELLERY_LAB_GROWN", "PEARL" -> true;
            case "WATCH" -> positive(metalAmount) || positive(stoneAmount);
            default -> false;
        };
    }

    /**
     * Maps the accounting {@code CommodityDto.type} onto a goAML {@code goods_services.item_type} code from
     * the XSD enumeration (GOLD, PLTNM, SLVER, DIMND, GEM, JEWEL, WATCH, …). Note: {@code METAL} is coarser
     * than goAML's per-metal codes, so it defaults to {@code GOLD} (the common bullion case) — refining to
     * SLVER/PLTNM needs the masters metal type, a future enhancement.
     */
    static String toItemType(String commodityType) {
        if (commodityType == null) {
            return "GOLD";
        }
        return switch (commodityType) {
            case "METAL" -> "GOLD";
            case "COLOR_STONE", "PEARL" -> "GEM";
            case "LOOSE_DIAMOND_NATURAL", "LOOSE_DIAMOND_LAB_GROWN" -> "DIMND";
            case "DIAMOND_JEWELLERY_NATURAL", "DIAMOND_JEWELLERY_LAB_GROWN" -> "JEWEL";
            case "WATCH" -> "WATCH";
            default -> "GOLD";
        };
    }

    private static boolean positive(BigDecimal v) {
        return v != null && v.compareTo(BigDecimal.ZERO) > 0;
    }
}
