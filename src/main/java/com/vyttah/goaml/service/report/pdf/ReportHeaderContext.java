package com.vyttah.goaml.service.report.pdf;

/**
 * The reporting-entity + generation context shown in the PDF letterhead header/footer — the goAML analog of
 * the AML customer-service {@code ReportCompanyHeader}. goAML's tenant model is thinner than the AML
 * {@code Company} (no stored address/TRN), so the header shows what the reporting entity's goAML record
 * holds: its name, the FIU-assigned reporting-entity id, and the jurisdiction. Null-safe blanks disappear
 * from the header rather than leaving empty lines.
 *
 * @param reportingEntityName the tenant / reporting-entity display name (letterhead line 1)
 * @param rentityId           the FIU-assigned reporting-entity id (0/absent → hidden)
 * @param jurisdictionCode    the jurisdiction (e.g. {@code ae}) the report is filed under
 * @param generatedBy         who generated the PDF (footer) — user email/name, or a system label
 * @param generatedAt         formatted generation timestamp (footer)
 */
record ReportHeaderContext(
        String reportingEntityName,
        Integer rentityId,
        String jurisdictionCode,
        String generatedBy,
        String generatedAt) {

    /** Company/entity name line — blank when unknown. */
    String nameLine() {
        return clean(reportingEntityName);
    }

    /** The identity lines under the name: FIU reporting-entity id + jurisdiction, each skipped when absent. */
    String identityText() {
        StringBuilder sb = new StringBuilder();
        if (rentityId != null && rentityId > 0) {
            sb.append("Reporting Entity ID: ").append(rentityId);
        }
        String jur = clean(jurisdictionCode);
        if (!jur.isEmpty()) {
            if (sb.length() > 0) {
                sb.append('\n');
            }
            sb.append("Jurisdiction: ").append(jur.toUpperCase());
        }
        return sb.toString();
    }

    String footerText() {
        String who = clean(generatedBy);
        String when = clean(generatedAt);
        if (when.isEmpty()) {
            return who;
        }
        return who.isEmpty() ? when : when + "    " + who;
    }

    private static String clean(String v) {
        return v == null ? "" : v.replaceAll("\\s+", " ").trim();
    }
}
