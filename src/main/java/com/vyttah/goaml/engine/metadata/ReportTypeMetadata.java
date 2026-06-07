package com.vyttah.goaml.engine.metadata;

import com.vyttah.goaml.domain.generated.ReportType;

import java.util.Set;

/**
 * Authoritative metadata about goAML report types: which shape they carry (transaction vs activity) and
 * which conditional header fields they require. This is the <em>single source of truth</em> consumed by
 * both {@link com.vyttah.goaml.engine.validation.ReportValidator} (to gate reports) and the MCP
 * {@code goaml_describe_report_type} tool (to teach an agent how to build a correct report up front), so
 * the two never drift.
 */
public final class ReportTypeMetadata {

    /** Transaction-based report shapes (carry {@code <transaction>}). */
    public static final Set<ReportType> TRANSACTION_CODES =
            Set.of(ReportType.STR, ReportType.AIFT, ReportType.ECDDT);
    /** Activity-based report shapes (carry {@code <activity>}). */
    public static final Set<ReportType> ACTIVITY_CODES =
            Set.of(ReportType.SAR, ReportType.AIF, ReportType.ECDD, ReportType.DPMSR);
    /** Codes that must reference an originating FIU request ({@code fiu_ref_number}). */
    public static final Set<ReportType> FIU_REF_REQUIRED =
            Set.of(ReportType.AIF, ReportType.AIFT, ReportType.ECDD, ReportType.ECDDT);
    /** Codes that require location / reason / action on the report header. */
    public static final Set<ReportType> LOCATION_REASON_ACTION_REQUIRED =
            Set.of(ReportType.STR, ReportType.SAR);

    private ReportTypeMetadata() {}

    /** The structural family a report code belongs to. */
    public enum Shape { TRANSACTION, ACTIVITY, OTHER }

    public static Shape shapeOf(ReportType code) {
        if (TRANSACTION_CODES.contains(code)) {
            return Shape.TRANSACTION;
        }
        if (ACTIVITY_CODES.contains(code)) {
            return Shape.ACTIVITY;
        }
        return Shape.OTHER;
    }

    public static boolean isTransactionShaped(ReportType code) {
        return TRANSACTION_CODES.contains(code);
    }

    public static boolean isActivityShaped(ReportType code) {
        return ACTIVITY_CODES.contains(code);
    }

    public static boolean requiresFiuRef(ReportType code) {
        return FIU_REF_REQUIRED.contains(code);
    }

    public static boolean requiresLocationReasonAction(ReportType code) {
        return LOCATION_REASON_ACTION_REQUIRED.contains(code);
    }

    /** A flat, serialisable description of one report type (for the describe tool). */
    public record Descriptor(
            String code,
            String shape,
            boolean fiuRefRequired,
            boolean locationReasonActionRequired) {}

    public static Descriptor describe(ReportType code) {
        return new Descriptor(
                code.value(),
                shapeOf(code).name(),
                requiresFiuRef(code),
                requiresLocationReasonAction(code));
    }
}
