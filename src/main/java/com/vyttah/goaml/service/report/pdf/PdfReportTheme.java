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
    static final Color LABEL_TEXT = Color.BLACK;
    static final Color VALUE_TEXT = new Color(51, 51, 51);
    static final Color MUTED_TEXT = new Color(120, 120, 120);

    // A4 with the same generous top margin the letterhead header needs (left, right, top, bottom).
    static final float MARGIN_LEFT = 24f;
    static final float MARGIN_RIGHT = 24f;
    static final float MARGIN_TOP = 108f;
    static final float MARGIN_BOTTOM = 44f;

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
