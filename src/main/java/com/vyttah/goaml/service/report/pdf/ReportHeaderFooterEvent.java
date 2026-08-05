package com.vyttah.goaml.service.report.pdf;

import com.lowagie.text.Chunk;
import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.Image;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.ColumnText;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfPageEventHelper;
import com.lowagie.text.pdf.PdfTemplate;
import com.lowagie.text.pdf.PdfWriter;

import java.awt.Color;

/**
 * The letterhead header + footer drawn on every page — the same construction the AML customer-service
 * reports use (logo + reporting-entity info block on a light-blue band, a page-1 title bar above it, and a
 * footer with the generation stamp on the left and "Page X of N" on the right). Ported to goAML and
 * sourced from {@link ReportHeaderContext} (goAML's tenant identity) + a caller-supplied report title.
 */
final class ReportHeaderFooterEvent extends PdfPageEventHelper {

    private final String title;
    private final ReportHeaderContext ctx;
    private PdfTemplate totalPagesTemplate;

    ReportHeaderFooterEvent(String title, ReportHeaderContext ctx) {
        this.title = title;
        this.ctx = ctx;
    }

    @Override
    public void onOpenDocument(PdfWriter writer, Document document) {
        totalPagesTemplate = writer.getDirectContent().createTemplate(30, 10);
    }

    @Override
    public void onStartPage(PdfWriter writer, Document document) {
        try {
            float usable = document.getPageSize().getWidth() - document.leftMargin() - document.rightMargin();
            float topY = document.getPageSize().getHeight() - 2f;

            Font infoLabelFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10.5f, Color.BLACK);
            Font infoValueFont = FontFactory.getFont(FontFactory.HELVETICA, 9f, new Color(51, 51, 51));

            // Page-1 title bar (blue-underlined band) above the letterhead.
            if (writer.getPageNumber() == 1) {
                PdfPTable titleTable = new PdfPTable(1);
                titleTable.setTotalWidth(usable);
                PdfPCell titleCell = new PdfPCell(new Phrase(title, PdfReportTheme.titleFont()));
                titleCell.setBackgroundColor(PdfReportTheme.HEADER_BG);
                titleCell.setHorizontalAlignment(Element.ALIGN_CENTER);
                titleCell.setPaddingTop(8f);
                titleCell.setPaddingBottom(8f);
                titleCell.setBorderWidthTop(0f);
                titleCell.setBorderWidthLeft(0f);
                titleCell.setBorderWidthRight(0f);
                titleCell.setBorderWidthBottom(3f);
                titleCell.setBorderColorBottom(PdfReportTheme.PRIMARY_BLUE);
                titleTable.addCell(titleCell);
                titleTable.writeSelectedRows(0, -1, document.leftMargin(), topY, writer.getDirectContent());
                topY -= titleTable.getTotalHeight();
            }

            PdfPTable header = new PdfPTable(2);
            header.setTotalWidth(usable);
            header.setWidths(new float[]{3.5f, 5.5f});

            // Logo cell (left) — the shared Vyttah logo, or a text fallback when the asset is missing.
            PdfPCell logoCell;
            if (PdfReportTheme.LOGO_BYTES.length > 0) {
                Image logo = Image.getInstance(PdfReportTheme.LOGO_BYTES);
                logo.scaleToFit(160, 46);
                logoCell = new PdfPCell(logo, false);
            } else {
                logoCell = new PdfPCell(new Phrase("Vyttah",
                        FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18f, PdfReportTheme.PRIMARY_BLUE)));
            }
            logoCell.setBackgroundColor(PdfReportTheme.HEADER_BG);
            logoCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
            logoCell.setPaddingLeft(15f);
            logoCell.setPaddingTop(8f);
            logoCell.setPaddingBottom(8f);
            logoCell.setBorder(Rectangle.NO_BORDER);
            header.addCell(logoCell);

            // Reporting-entity info block (right) — name + FIU id/jurisdiction.
            PdfPTable infoTable = new PdfPTable(2);
            infoTable.setWidths(new float[]{1.2f, 1});

            Paragraph nameP = new Paragraph();
            nameP.setLeading(13f);
            String name = ctx.nameLine();
            nameP.add(new Chunk(name.isEmpty() ? "" : name + "\n", infoLabelFont));
            nameP.add(new Chunk(ctx.identityText(), infoValueFont));
            PdfPCell nameCell = new PdfPCell(nameP);
            nameCell.setBorder(Rectangle.NO_BORDER);
            nameCell.setPadding(5f);
            nameCell.setBackgroundColor(PdfReportTheme.HEADER_BG);
            infoTable.addCell(nameCell);

            Paragraph filedP = new Paragraph();
            filedP.setLeading(13f);
            filedP.add(new Chunk("UAE FIU goAML\nDPMSR filing", infoValueFont));
            PdfPCell filedCell = new PdfPCell(filedP);
            filedCell.setBorder(Rectangle.NO_BORDER);
            filedCell.setPadding(5f);
            filedCell.setBackgroundColor(PdfReportTheme.HEADER_BG);
            infoTable.addCell(filedCell);

            PdfPCell infoCell = new PdfPCell(infoTable);
            infoCell.setBackgroundColor(PdfReportTheme.HEADER_BG);
            infoCell.setBorder(Rectangle.NO_BORDER);
            infoCell.setPadding(4f);
            header.addCell(infoCell);

            header.writeSelectedRows(0, -1, document.leftMargin(), topY, writer.getDirectContent());
        } catch (Exception ignored) {
            // a header failure must never abort the document
        }
    }

    @Override
    public void onEndPage(PdfWriter writer, Document document) {
        try {
            Font footerFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8.5f, Color.BLACK);
            float pageWidth = document.getPageSize().getWidth();
            float bottom = 32f;

            PdfPTable footer = new PdfPTable(2);
            footer.setTotalWidth(pageWidth);
            footer.setWidths(new float[]{3, 1});

            PdfPCell leftCell = new PdfPCell(new Phrase(ctx.footerText(), footerFont));
            leftCell.setBackgroundColor(PdfReportTheme.HEADER_BG);
            leftCell.setBorder(Rectangle.NO_BORDER);
            leftCell.setPaddingTop(5f);
            leftCell.setPaddingBottom(5f);
            leftCell.setPaddingLeft(20f);
            leftCell.setHorizontalAlignment(Element.ALIGN_LEFT);
            footer.addCell(leftCell);

            Phrase pagePhrase = new Phrase("Page " + writer.getPageNumber() + " of ", footerFont);
            Image img = Image.getInstance(totalPagesTemplate);
            img.scaleToFit(20, 9);
            pagePhrase.add(new Chunk(img, 0, -1));
            PdfPCell rightCell = new PdfPCell(pagePhrase);
            rightCell.setBackgroundColor(PdfReportTheme.HEADER_BG);
            rightCell.setBorder(Rectangle.NO_BORDER);
            rightCell.setPaddingTop(5f);
            rightCell.setPaddingBottom(5f);
            rightCell.setPaddingRight(20f);
            rightCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
            footer.addCell(rightCell);

            footer.writeSelectedRows(0, -1, 0f, bottom, writer.getDirectContent());
        } catch (Exception ignored) {
            // a footer failure must never abort the document
        }
    }

    @Override
    public void onCloseDocument(PdfWriter writer, Document document) {
        Font footerFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8.5f, Color.BLACK);
        ColumnText.showTextAligned(totalPagesTemplate, Element.ALIGN_LEFT,
                new Phrase(String.valueOf(writer.getPageNumber() - 1), footerFont), 2, 2, 0);
    }
}
