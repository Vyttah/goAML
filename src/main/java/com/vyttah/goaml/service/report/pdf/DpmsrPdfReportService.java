package com.vyttah.goaml.service.report.pdf;

import com.fasterxml.jackson.databind.JsonNode;
import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.vyttah.goaml.domain.generated.ActivityType;
import com.vyttah.goaml.domain.generated.CurrencyType;
import com.vyttah.goaml.domain.generated.EntityRelatedPersonType;
import com.vyttah.goaml.domain.generated.Report;
import com.vyttah.goaml.domain.generated.ReportPartyType;
import com.vyttah.goaml.domain.generated.TAddress;
import com.vyttah.goaml.domain.generated.TEntity;
import com.vyttah.goaml.domain.generated.TEntityMyClient;
import com.vyttah.goaml.domain.generated.TPerson;
import com.vyttah.goaml.domain.generated.TPersonMyClient;
import com.vyttah.goaml.domain.generated.TAccount;
import com.vyttah.goaml.domain.generated.TAccountMyClient;
import com.vyttah.goaml.domain.generated.TTransItem;
import com.vyttah.goaml.engine.lookup.LookupService;
import com.vyttah.goaml.engine.validation.ValidationMessage;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Renders a DPMSR report to a print-ready PDF using OpenPDF — the same engine + house style (theme, logo,
 * letterhead header/footer) the AML customer-service reports use, but with content + layout designed here
 * specifically for the goAML DPMSR (report headers, location of incident, involved parties incl. the
 * portal's my-client / lenient / account shapes, related persons, goods and services, reason for reporting,
 * plus the captured-not-filed internal details and any validation findings).
 *
 * <p>Input is the typed JAXB {@link Report} (unmarshalled from the stored/previewed goAML XML), so the PDF
 * reflects <em>exactly</em> what would be filed. FIU codes (legal form, indicator, item type/status,
 * country, currency) are resolved to their human labels via {@link LookupService}.
 */
@Service
public class DpmsrPdfReportService {

    private static final String REPORT_TITLE = "Dealers in Precious Metals and Stone Report (DPMSR)";
    private static final String DASH = "—";
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH);

    private final LookupService lookupService;

    public DpmsrPdfReportService(LookupService lookupService) {
        this.lookupService = lookupService;
    }

    /**
     * @param report        the typed report (from unmarshalled XML) — the source of truth for content
     * @param ctx           the letterhead header/footer context (reporting entity + generation stamp)
     * @param statusLabel   the report status to show in the banner (VALID / INVALID / SUBMITTED / …); may be null
     * @param jurisdiction  jurisdiction for FIU code→label resolution (e.g. "ae")
     * @param clientMetadata the captured-not-filed JSON (rendered in "Internal details"); may be null
     * @param messages      validation findings to print (empty → the section is omitted)
     */
    public byte[] render(Report report, ReportHeaderContext ctx, String statusLabel, String jurisdiction,
                         JsonNode clientMetadata, List<ValidationMessage> messages) {
        Labels labels = new Labels(jurisdiction);
        Document document = new Document(PageSize.A4, PdfReportTheme.MARGIN_LEFT, PdfReportTheme.MARGIN_RIGHT,
                PdfReportTheme.MARGIN_TOP, PdfReportTheme.MARGIN_BOTTOM);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            PdfWriter writer = PdfWriter.getInstance(document, out);
            writer.setPageEvent(new ReportHeaderFooterEvent(REPORT_TITLE, ctx));
            document.addTitle(REPORT_TITLE + " - " + valueOr(report.getEntityReference()));
            document.open();

            banner(document, report, statusLabel);
            reportHeaders(document, report);
            location(document, report, labels);
            parties(document, report, labels);
            goods(document, report, labels);
            reasonForReporting(document, report, labels);
            internalDetails(document, clientMetadata);
            validation(document, messages);

            document.close();
            return out.toByteArray();
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to generate the DPMSR report PDF", ex);
        }
    }

    // ---- sections -------------------------------------------------------------------------------------

    private void banner(Document document, Report report, String statusLabel) throws Exception {
        PdfPTable banner = fullWidth(4);
        banner.setWidths(new float[]{1.3f, 1.6f, 1f, 1.1f});
        bannerCell(banner, "Report reference", valueOr(report.getEntityReference()));
        bannerCell(banner, "Reporting entity ID", report.getRentityId() > 0 ? String.valueOf(report.getRentityId()) : DASH);
        bannerCell(banner, "Local currency", currency(report.getCurrencyCodeLocal()));
        bannerCell(banner, "Status", valueOr(statusLabel));
        document.add(banner);
        document.add(spacer(4f));
    }

    private void reportHeaders(Document document, Report report) throws Exception {
        sectionHeader(document, "Report headers");
        Map<String, String> kv = new LinkedHashMap<>();
        kv.put("Report reference", valueOr(report.getEntityReference()));
        kv.put("Report date", date(reportDate(report)));
        kv.put("Reporting entity branch", valueOr(report.getRentityBranch()));
        kv.put("FIU reference", valueOr(report.getFiuRefNumber()));
        keyValueGrid(document, kv);
        longText(document, "Description of the report", report.getReason());
        longText(document, "Action taken by the reporting entity", report.getAction());
    }

    private void location(Document document, Report report, Labels labels) throws Exception {
        TAddress loc = report.getLocation();
        if (loc == null) {
            return;
        }
        sectionHeader(document, "Location of incident");
        keyValueGrid(document, addressKv(loc, labels));
    }

    private void parties(Document document, Report report, Labels labels) throws Exception {
        ActivityType activity = report.getReportActivity();
        List<ReportPartyType> list = activity == null || activity.getReportParties() == null
                ? List.of() : activity.getReportParties().getReportParty();
        int n = 0;
        for (ReportPartyType party : list) {
            n++;
            party(document, party, n, labels);
        }
    }

    private void party(Document document, ReportPartyType party, int index, Labels labels) throws Exception {
        TEntityMyClient emc = party.getEntityMyClient();
        TEntity ent = party.getEntity();
        TPersonMyClient pmc = party.getPersonMyClient();
        TPerson per = party.getPerson();
        TAccount acc = party.getAccount();
        TAccountMyClient amc = party.getAccountMyClient();

        String kind;
        Map<String, String> kv = new LinkedHashMap<>();
        List<EntityRelatedPersonType> related = List.of();

        if (emc != null) {
            kind = "Entity (my client)";
            entityKv(kv, emc.getName(), emc.getCommercialName(), emc.getIncorporationLegalForm(),
                    emc.getIncorporationNumber(), emc.getBusiness(), emc.getIncorporationState(),
                    emc.getIncorporationCountryCode(), emc.getIncorporationDate(), emc.getTaxNumber(),
                    emc.getTaxRegNumber(), labels);
            related = emc.getRelatedPersons() == null ? List.of() : emc.getRelatedPersons().getEntityRelatedPerson();
        } else if (ent != null) {
            kind = "Entity";
            entityKv(kv, ent.getName(), null, ent.getIncorporationLegalForm(), ent.getIncorporationNumber(),
                    ent.getBusiness(), null, ent.getIncorporationCountryCode(), ent.getIncorporationDate(),
                    null, null, labels);
            related = ent.getRelatedPersons() == null ? List.of() : ent.getRelatedPersons().getEntityRelatedPerson();
        } else if (pmc != null) {
            kind = "Person (my client)";
            personKv(kv, pmc.getFirstName(), pmc.getLastName(), pmc.getGender(), pmc.getBirthdate(),
                    pmc.getNationality1(), pmc.getResidence(), pmc.getIdNumber(), pmc.getTaxRegNumber(), labels);
        } else if (per != null) {
            kind = "Person";
            personKv(kv, per.getFirstName(), per.getLastName(), per.getGender(), per.getBirthdate(),
                    per.getNationality1(), per.getResidence(), per.getIdNumber(), null, labels);
        } else if (acc != null || amc != null) {
            kind = amc != null ? "Account (my client)" : "Account";
            String inst = amc != null ? amc.getInstitutionName() : acc.getInstitutionName();
            String swift = amc != null ? amc.getSwift() : acc.getSwift();
            String number = amc != null ? amc.getAccount() : acc.getAccount();
            String accName = amc != null ? amc.getAccountName() : acc.getAccountName();
            String iban = amc != null ? amc.getIban() : acc.getIban();
            kv.put("Institution", valueOr(inst));
            kv.put("SWIFT", valueOr(swift));
            kv.put("Account number", valueOr(number));
            kv.put("Account name", valueOr(accName));
            kv.put("IBAN", valueOr(iban));
        } else {
            kind = "Party";
        }

        sectionHeader(document, "Involved party " + index + "  —  " + kind);
        // The party's role narrative in this transaction (portal "Reason") + the payment/beneficiary comments.
        if (notBlank(party.getReason()) || notBlank(party.getComments())) {
            Map<String, String> head = new LinkedHashMap<>();
            head.put("Reason (role in transaction)", valueOr(party.getReason()));
            keyValueGrid(document, head);
        }
        keyValueGrid(document, kv);
        if (notBlank(party.getComments())) {
            longText(document, "Comments", party.getComments());
        }
        relatedPersons(document, related, labels);
    }

    private void relatedPersons(Document document, List<EntityRelatedPersonType> related, Labels labels)
            throws Exception {
        if (related == null || related.isEmpty()) {
            return;
        }
        subHeader(document, "Related persons");
        PdfPTable t = fullWidth(6);
        t.setWidths(new float[]{2.2f, 1.3f, 1.1f, 1.2f, 1f, 1.3f});
        headerRow(t, "Name", "Role", "Share %", "Date of birth", "Nationality", "ID number");
        int row = 0;
        for (EntityRelatedPersonType rp : related) {
            TPerson p = rp.getPerson();
            dataRow(t, row++,
                    p == null ? DASH : join(p.getFirstName(), p.getLastName()),
                    rp.getRole() == null ? DASH : labels.role(rp.getRole().value()),
                    rp.getSharePercentage() == null ? DASH : plain(rp.getSharePercentage()),
                    p == null ? DASH : date(p.getBirthdate()),
                    p == null ? DASH : labels.country(p.getNationality1()),
                    p == null ? DASH : valueOr(p.getIdNumber()));
        }
        document.add(t);
        document.add(spacer(PdfReportTheme.GAP_BLOCK));
    }

    private void goods(Document document, Report report, Labels labels) throws Exception {
        ActivityType activity = report.getReportActivity();
        List<TTransItem> items = activity == null || activity.getGoodsServices() == null
                ? List.of() : activity.getGoodsServices().getItem();
        if (items.isEmpty()) {
            return;
        }
        sectionHeader(document, "Goods and services");
        int n = 0;
        for (TTransItem item : items) {
            n++;
            if (n > 1) {
                document.add(spacer(8f));
            }
            subHeader(document, "Item " + n + "  —  " + labels.itemType(item.getItemType()));
            Map<String, String> kv = new LinkedHashMap<>();
            kv.put("Item type", labels.itemType(item.getItemType()));
            kv.put("Status", labels.itemStatus(item.getStatusCode()));
            kv.put("Estimated value", amount(item.getEstimatedValue()));
            kv.put("Invoice amount", amount(item.getDisposedValue()));
            kv.put("Currency", currency(item.getCurrencyCode()));
            kv.put("Size", item.getSize() == null ? DASH : plain(item.getSize()) + sizeUom(item));
            kv.put("Seller (previously registered to)", valueOr(item.getPreviouslyRegisteredTo()));
            kv.put("Buyer (presently registered to)", valueOr(item.getPresentlyRegisteredTo()));
            kv.put("Registration number", valueOr(item.getRegistrationNumber()));
            kv.put("Identification number", valueOr(item.getIdentificationNumber()));
            kv.put("Contract date", date(item.getRegistrationDate()));
            keyValueGrid(document, kv);
            longText(document, "Description", item.getDescription());
            longText(document, "Comments", item.getComments());
        }
    }

    private void reasonForReporting(Document document, Report report, Labels labels) throws Exception {
        List<String> indicators = report.getReportIndicators() == null
                ? List.of() : report.getReportIndicators().getIndicator();
        if (indicators.isEmpty()) {
            return;
        }
        sectionHeader(document, "Reason for reporting");
        PdfPTable t = fullWidth(2);
        t.setWidths(new float[]{1f, 5f});
        headerRow(t, "Indicator", "Description");
        int row = 0;
        for (String code : indicators) {
            dataRow(t, row++, valueOr(code), labels.indicator(code));
        }
        document.add(t);
        document.add(spacer(PdfReportTheme.GAP_BLOCK));
    }

    private void internalDetails(Document document, JsonNode clientMetadata) throws Exception {
        if (clientMetadata == null || !clientMetadata.isObject() || clientMetadata.isEmpty()) {
            return;
        }
        sectionHeader(document, "Internal details (captured, not filed to the FIU)");
        Map<String, String> kv = new LinkedHashMap<>();
        clientMetadata.fields().forEachRemaining(e -> {
            JsonNode v = e.getValue();
            if (v != null && !v.isNull() && !v.isObject() && !v.isArray() && notBlank(v.asText())) {
                kv.put(humanize(e.getKey()), v.asText());
            }
        });
        if (kv.isEmpty()) {
            return;
        }
        keyValueGrid(document, kv);
        note(document, "These fields are kept with your record for internal reference. They are not part of "
                + "the FIU goAML schema and are never included in the filed report.");
    }

    private void validation(Document document, List<ValidationMessage> messages) throws Exception {
        if (messages == null || messages.isEmpty()) {
            return;
        }
        sectionHeader(document, "Validation findings");
        PdfPTable t = fullWidth(3);
        t.setWidths(new float[]{1f, 1.4f, 4f});
        headerRow(t, "Severity", "Field", "Message");
        int row = 0;
        for (ValidationMessage m : messages) {
            dataRow(t, row++, valueOr(String.valueOf(m.severity())), valueOr(m.path()), valueOr(m.message()));
        }
        document.add(t);
        document.add(spacer(PdfReportTheme.GAP_BLOCK));
    }

    // ---- kv builders ----------------------------------------------------------------------------------

    private void entityKv(Map<String, String> kv, String name, String commercialName, String legalForm,
                          String incNumber, String business, String incState, String incCountry,
                          OffsetDateTime incDate, String taxNumber, String pepChar, Labels labels) {
        kv.put("Legal name", valueOr(name));
        if (commercialName != null) {
            kv.put("Commercial name", valueOr(commercialName));
        }
        kv.put("Business activity", labels.legalForm(legalForm));
        kv.put("Licensing authority", valueOr(business));
        kv.put("Trade license / incorporation no.", valueOr(incNumber));
        if (incState != null) {
            kv.put("Place of incorporation", valueOr(incState));
        }
        kv.put("Country of incorporation", labels.country(incCountry));
        kv.put("Establishment date", date(incDate));
        if (taxNumber != null) {
            kv.put("Tax number", valueOr(taxNumber));
        }
        if (pepChar != null) {
            kv.put("PEP", pepLabel(pepChar));
        }
    }

    private void personKv(Map<String, String> kv, String first, String last, String gender,
                          OffsetDateTime birthdate, String nationality, String residence, String idNumber,
                          String pepChar, Labels labels) {
        kv.put("Name", join(first, last));
        kv.put("Gender", gender(gender));
        kv.put("Date of birth", date(birthdate));
        kv.put("Nationality", labels.country(nationality));
        kv.put("Country of residence", labels.country(residence));
        kv.put("ID number", valueOr(idNumber));
        if (pepChar != null) {
            kv.put("PEP", pepLabel(pepChar));
        }
    }

    private Map<String, String> addressKv(TAddress a, Labels labels) {
        Map<String, String> kv = new LinkedHashMap<>();
        kv.put("Type", "BU".equals(a.getAddressType()) ? "Business" : valueOr(a.getAddressType()));
        kv.put("Address", valueOr(a.getAddress()));
        kv.put("City", valueOr(a.getCity()));
        kv.put("Country", labels.country(a.getCountryCode()));
        kv.put("State / Emirate", valueOr(a.getState()));
        return kv;
    }

    // ---- low-level layout helpers --------------------------------------------------------------------

    private void sectionHeader(Document document, String title) throws Exception {
        PdfPTable t = fullWidth(1);
        PdfPCell c = new PdfPCell(new Phrase(title.toUpperCase(Locale.ENGLISH), PdfReportTheme.sectionFont()));
        c.setBackgroundColor(PdfReportTheme.SECTION_BG);
        c.setPaddingLeft(PdfReportTheme.CELL_PAD_X);
        c.setPaddingRight(PdfReportTheme.CELL_PAD_X);
        c.setPaddingTop(7f);
        c.setPaddingBottom(7.5f);
        c.setBorder(com.lowagie.text.Rectangle.NO_BORDER);
        t.addCell(c);
        document.add(spacer(PdfReportTheme.GAP_BEFORE_SECTION));
        document.add(t);
        document.add(spacer(PdfReportTheme.GAP_AFTER_SECTION));
    }

    private void subHeader(Document document, String title) throws Exception {
        Paragraph p = new Paragraph(title,
                com.lowagie.text.FontFactory.getFont(com.lowagie.text.FontFactory.HELVETICA_BOLD, 9.5f,
                        PdfReportTheme.PRIMARY_BLUE));
        p.setSpacingBefore(6f);
        p.setSpacingAfter(4f);
        document.add(p);
    }

    /** A two-column-pair label/value grid (4 table columns → two label:value pairs per row). */
    private void keyValueGrid(Document document, Map<String, String> kv) throws Exception {
        if (kv.isEmpty()) {
            return;
        }
        PdfPTable t = fullWidth(4);
        t.setWidths(new float[]{1.15f, 1.35f, 1.15f, 1.35f});
        List<Map.Entry<String, String>> entries = new ArrayList<>(kv.entrySet());
        for (int i = 0; i < entries.size(); i += 2) {
            labelValue(t, entries.get(i));
            if (i + 1 < entries.size()) {
                labelValue(t, entries.get(i + 1));
            } else {
                blank(t);
                blank(t);
            }
        }
        document.add(t);
        document.add(spacer(PdfReportTheme.GAP_BLOCK));
    }

    private void labelValue(PdfPTable t, Map.Entry<String, String> e) {
        PdfPCell l = new PdfPCell(new Phrase(e.getKey(), PdfReportTheme.labelFont()));
        l.setBorderColor(PdfReportTheme.BORDER_COLOR);
        l.setBackgroundColor(PdfReportTheme.LABEL_BG);
        l.setVerticalAlignment(Element.ALIGN_MIDDLE);
        l.setMinimumHeight(PdfReportTheme.ROW_MIN_HEIGHT);
        pad(l);
        t.addCell(l);
        PdfPCell v = new PdfPCell(new Phrase(e.getValue(), PdfReportTheme.valueFont()));
        v.setBorderColor(PdfReportTheme.BORDER_COLOR);
        v.setVerticalAlignment(Element.ALIGN_MIDDLE);
        v.setMinimumHeight(PdfReportTheme.ROW_MIN_HEIGHT);
        pad(v);
        t.addCell(v);
    }

    /** The shared cell padding: more on the x-axis than the y-axis, so rows breathe without growing tall. */
    private void pad(PdfPCell c) {
        c.setPaddingLeft(PdfReportTheme.CELL_PAD_X);
        c.setPaddingRight(PdfReportTheme.CELL_PAD_X);
        c.setPaddingTop(PdfReportTheme.CELL_PAD_Y);
        c.setPaddingBottom(PdfReportTheme.CELL_PAD_Y);
    }

    private void blank(PdfPTable t) {
        PdfPCell c = new PdfPCell(new Phrase(""));
        c.setBorder(com.lowagie.text.Rectangle.NO_BORDER);
        t.addCell(c);
    }

    private void longText(Document document, String label, String text) throws Exception {
        if (!notBlank(text)) {
            return;
        }
        PdfPTable t = fullWidth(1);
        PdfPCell l = new PdfPCell(new Phrase(label, PdfReportTheme.labelFont()));
        l.setBorder(com.lowagie.text.Rectangle.NO_BORDER);
        l.setPaddingLeft(1f);
        l.setPaddingBottom(3f);
        t.addCell(l);
        PdfPCell v = new PdfPCell(new Phrase(text.trim(), PdfReportTheme.valueFont()));
        v.setBorderColor(PdfReportTheme.BORDER_COLOR);
        v.setLeading(0f, 1.25f);
        pad(v);
        t.addCell(v);
        document.add(t);
        document.add(spacer(PdfReportTheme.GAP_BLOCK));
    }

    private void note(Document document, String text) throws Exception {
        Paragraph p = new Paragraph(text, PdfReportTheme.noteFont());
        p.setLeading(11f);
        p.setSpacingBefore(4f);
        document.add(p);
        document.add(spacer(3f));
    }

    private void bannerCell(PdfPTable t, String label, String value) {
        PdfPCell c = new PdfPCell();
        c.setBackgroundColor(PdfReportTheme.HEADER_BG);
        c.setBorderColor(PdfReportTheme.BORDER_COLOR);
        c.setPaddingLeft(PdfReportTheme.CELL_PAD_X);
        c.setPaddingRight(PdfReportTheme.CELL_PAD_X);
        c.setPaddingTop(8f);
        c.setPaddingBottom(8f);
        Paragraph p = new Paragraph();
        p.setLeading(13f);
        p.add(new Phrase(label + "\n", PdfReportTheme.bannerLabelFont()));
        p.add(new Phrase(value, PdfReportTheme.bannerValueFont()));
        c.addElement(p);
        t.addCell(c);
    }

    private void headerRow(PdfPTable t, String... headers) {
        for (String h : headers) {
            PdfPCell c = new PdfPCell(new Phrase(h,
                    com.lowagie.text.FontFactory.getFont(com.lowagie.text.FontFactory.HELVETICA_BOLD, 8.5f,
                            Color.WHITE)));
            c.setBackgroundColor(PdfReportTheme.TABLE_HEAD_BG);
            c.setBorderColor(PdfReportTheme.TABLE_HEAD_BG);
            c.setVerticalAlignment(Element.ALIGN_MIDDLE);
            c.setMinimumHeight(PdfReportTheme.ROW_MIN_HEIGHT);
            pad(c);
            t.addCell(c);
        }
        t.setHeaderRows(1); // repeat the column headings when a long table breaks across a page
    }

    /** One striped data row — the alternating background makes wide tables easy to scan across. */
    private void dataRow(PdfPTable t, int rowIndex, String... values) {
        Color bg = rowIndex % 2 == 1 ? PdfReportTheme.ROW_ALT_BG : Color.WHITE;
        for (String v : values) {
            dataCell(t, v, bg);
        }
    }

    private void dataCell(PdfPTable t, String value) {
        dataCell(t, value, Color.WHITE);
    }

    private void dataCell(PdfPTable t, String value, Color bg) {
        PdfPCell c = new PdfPCell(new Phrase(value, PdfReportTheme.valueFont()));
        c.setBorderColor(PdfReportTheme.BORDER_COLOR);
        c.setBackgroundColor(bg);
        c.setVerticalAlignment(Element.ALIGN_MIDDLE);
        c.setMinimumHeight(PdfReportTheme.ROW_MIN_HEIGHT);
        pad(c);
        t.addCell(c);
    }

    private PdfPTable fullWidth(int cols) {
        PdfPTable t = new PdfPTable(cols);
        t.setWidthPercentage(100f);
        return t;
    }

    private Paragraph spacer(float height) {
        Paragraph p = new Paragraph(" ",
                com.lowagie.text.FontFactory.getFont(com.lowagie.text.FontFactory.HELVETICA, height * 0.5f));
        p.setLeading(height);
        return p;
    }

    // ---- value formatting ----------------------------------------------------------------------------

    private static String valueOr(String v) {
        return notBlank(v) ? v.trim() : DASH;
    }

    private static boolean notBlank(String v) {
        return v != null && !v.isBlank();
    }

    private static String join(String a, String b) {
        String s = ((a == null ? "" : a) + " " + (b == null ? "" : b)).trim();
        return s.isEmpty() ? DASH : s;
    }

    private static String date(OffsetDateTime d) {
        return d == null ? DASH : d.format(DATE_FMT);
    }

    private static OffsetDateTime reportDate(Report report) {
        return report.getSubmissionDate() != null ? report.getSubmissionDate() : report.getReportDate();
    }

    private static String amount(BigDecimal v) {
        if (v == null) {
            return DASH;
        }
        return String.format(Locale.US, "%,.2f", v);
    }

    private static String plain(BigDecimal v) {
        return v == null ? DASH : v.stripTrailingZeros().toPlainString();
    }

    private String sizeUom(TTransItem item) {
        return notBlank(item.getSizeUom()) ? " " + item.getSizeUom().trim() : "";
    }

    private static String currency(CurrencyType c) {
        return c == null ? DASH : c.value();
    }

    private static String gender(String g) {
        if ("M".equals(g)) {
            return "Male";
        }
        if ("F".equals(g)) {
            return "Female";
        }
        return notBlank(g) && !"-".equals(g) ? g : DASH;
    }

    private static String pepLabel(String pepChar) {
        if ("Y".equalsIgnoreCase(pepChar)) {
            return "Yes";
        }
        if ("N".equalsIgnoreCase(pepChar)) {
            return "No";
        }
        return valueOr(pepChar);
    }

    /** camelCase / snake_case metadata key → a spaced Title-ish label. */
    private static String humanize(String key) {
        if (key == null || key.isBlank()) {
            return "";
        }
        String spaced = key.replaceAll("([a-z0-9])([A-Z])", "$1 $2").replace('_', ' ').trim();
        return Character.toUpperCase(spaced.charAt(0)) + spaced.substring(1);
    }

    /** Resolves FIU codes to labels for a jurisdiction, cached per render. Falls back to the code itself. */
    private final class Labels {
        private final Map<String, Map<String, String>> bySet = new java.util.HashMap<>();
        private final String jurisdiction;

        Labels(String jurisdiction) {
            this.jurisdiction = jurisdiction == null || jurisdiction.isBlank() ? "ae" : jurisdiction;
        }

        private String label(String set, String code) {
            if (!notBlank(code)) {
                return DASH;
            }
            Map<String, String> map = bySet.computeIfAbsent(set, s -> {
                Map<String, String> m = new java.util.HashMap<>();
                lookupService.entries(jurisdiction, s).forEach(e -> m.put(e.code(), e.label()));
                return m;
            });
            String lbl = map.get(code);
            return lbl == null || lbl.equals(code) ? code : lbl;
        }

        String legalForm(String code) {
            return label("legal_forms", code);
        }

        String indicator(String code) {
            return label("report_indicators", code);
        }

        String itemType(String code) {
            return label("item_types", code);
        }

        String itemStatus(String code) {
            return label("item_status", code);
        }

        String role(String code) {
            return label("party_roles", code);
        }

        String country(String code) {
            return label("countries", code);
        }
    }
}
