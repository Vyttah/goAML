package com.vyttah.goaml.service.report.pdf;

import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;

import java.awt.Color;
import java.io.InputStream;

/**
 * Shared visual constants + fonts for the generated report PDFs, matching the AML customer-service report
 * house style (same OpenPDF engine, same Vyttah letterhead palette + Helvetica family + logo) so a DPMSR
 * report PDF looks like it came from the same suite. Content/layout of the DPMSR itself is designed
 * separately in {@link DpmsrPdfReportService}; this file is only the theme + shared plumbing.
 */
final class PdfReportTheme {

    private PdfReportTheme() {}

    // Vyttah report palette (identical to customer-service so the suite's PDFs are visually consistent).
    static final Color PRIMARY_BLUE = new Color(42, 67, 144);
    static final Color HEADER_BG = new Color(240, 247, 255);
    static final Color BORDER_COLOR = new Color(224, 224, 224);
    static final Color SECTION_BG = new Color(42, 67, 144);
    static final Color LABEL_BG = new Color(248, 250, 253);
    static final Color ROW_ALT_BG = new Color(248, 250, 253);
    static final Color TABLE_HEAD_BG = new Color(90, 99, 120);
    static final Color LABEL_TEXT = Color.BLACK;
    static final Color VALUE_TEXT = new Color(51, 51, 51);
    static final Color MUTED_TEXT = new Color(120, 120, 120);

    // A4 with the same generous top margin the letterhead header needs (left, right, top, bottom). The side
    // margins are roomy on purpose — the earlier report crowded the page edges, which read as congested.
    static final float MARGIN_LEFT = 40f;
    static final float MARGIN_RIGHT = 40f;
    static final float MARGIN_TOP = 112f;
    static final float MARGIN_BOTTOM = 54f;

    // Shared spacing rhythm. Cells use more horizontal than vertical padding so text has room to breathe on
    // both axes without the rows growing tall; the row minimum height keeps single-line values from cramping.
    static final float CELL_PAD_X = 10f;
    static final float CELL_PAD_Y = 5.5f;
    static final float ROW_MIN_HEIGHT = 18f;
    static final float GAP_BEFORE_SECTION = 12f;
    static final float GAP_AFTER_SECTION = 6f;
    static final float GAP_BLOCK = 8f;

    /** The shared Vyttah report logo, loaded once from the classpath (empty when absent — header falls back). */
    static final byte[] LOGO_BYTES = loadLogo();

    private static byte[] loadLogo() {
        try (InputStream is = PdfReportTheme.class.getResourceAsStream("/assets/reports_logo.jpeg")) {
            if (is != null) {
                return is.readAllBytes();
            }
        } catch (Exception ignored) {
            // fall through — the header renders the "Vyttah" text fallback
        }
        return new byte[0];
    }

    static Font sectionFont() {
        return FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11f, Color.WHITE);
    }

    static Font labelFont() {
        return FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9f, LABEL_TEXT);
    }

    static Font valueFont() {
        return FontFactory.getFont(FontFactory.HELVETICA, 9f, VALUE_TEXT);
    }

    static Font bannerLabelFont() {
        return FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8.5f, MUTED_TEXT);
    }

    static Font bannerValueFont() {
        return FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10.5f, PRIMARY_BLUE);
    }

    static Font titleFont() {
        return FontFactory.getFont(FontFactory.HELVETICA_BOLD, 15f, PRIMARY_BLUE);
    }

    static Font noteFont() {
        return FontFactory.getFont(FontFactory.HELVETICA_OBLIQUE, 8f, MUTED_TEXT);
    }
}
