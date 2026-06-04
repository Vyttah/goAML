package com.vyttah.goaml.b2b;

/**
 * The status of a submitted report, parsed from the goAML OData status feed
 * ({@code OdataReports?$filter=ReportKey eq '…'} → {@code value[0]}).
 *
 * @param reportKey the FIU's handle for the submission
 * @param status    the current status (e.g. {@code Processing}, {@code Accepted}, {@code Rejected})
 * @param errors    any error text the FIU attached, or {@code null}
 */
public record ReportStatus(String reportKey, String status, String errors) {
}
