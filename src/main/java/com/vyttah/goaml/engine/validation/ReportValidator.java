package com.vyttah.goaml.engine.validation;

import com.vyttah.goaml.domain.Report;
import com.vyttah.goaml.domain.activity.Activity;
import com.vyttah.goaml.domain.activity.ReportParty;
import com.vyttah.goaml.domain.common.GoodsServices;
import com.vyttah.goaml.domain.common.ReportingPerson;
import com.vyttah.goaml.domain.enums.ReportCode;
import com.vyttah.goaml.domain.transaction.TParty;
import com.vyttah.goaml.domain.transaction.Transaction;
import com.vyttah.goaml.engine.jurisdiction.JurisdictionConfig;
import com.vyttah.goaml.engine.jurisdiction.JurisdictionRegistry;
import com.vyttah.goaml.engine.lookup.LookupService;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

/**
 * Pre-submission business-rule validator. Applies schema-level and UAE-specific conditional rules
 * keyed by {@code report_code} + jurisdiction, returning a structured {@link ValidationResult}.
 *
 * <p>This is the business-rule gate that runs <em>before</em> the authoritative XSD gate
 * ({@code XsdSchemaValidator}, wired once the FIU XSD is supplied — plan Open Item #1) and the
 * Business Rejection Rules (BRRs, Open Item #3) which will extend the rule set here.
 */
@Component
public class ReportValidator {

    /** Transaction-based report shapes (carry {@code <transaction>}). */
    private static final Set<ReportCode> TRANSACTION_CODES = Set.of(ReportCode.STR, ReportCode.AIFT, ReportCode.ECDDT);
    /** Activity-based report shapes (carry {@code <activity>}). */
    private static final Set<ReportCode> ACTIVITY_CODES = Set.of(ReportCode.SAR, ReportCode.AIF, ReportCode.ECDD, ReportCode.DPMSR);
    /** Codes that must reference an originating FIU request. */
    private static final Set<ReportCode> FIU_REF_REQUIRED = Set.of(ReportCode.AIF, ReportCode.AIFT, ReportCode.ECDD, ReportCode.ECDDT);
    /** Codes that require location / reason / action on the report header. */
    private static final Set<ReportCode> LOCATION_REASON_ACTION_REQUIRED = Set.of(ReportCode.STR, ReportCode.SAR);

    private static final int MAX_ENTITY_REFERENCE = 255;
    private static final int MAX_FIU_REF = 255;
    private static final int MAX_TXN_NUMBER = 50;
    private static final int MAX_REASON = 4000;
    private static final int MAX_ACTION = 4000;

    private final JurisdictionRegistry jurisdictionRegistry;
    private final LookupService lookupService;

    public ReportValidator(JurisdictionRegistry jurisdictionRegistry, LookupService lookupService) {
        this.jurisdictionRegistry = jurisdictionRegistry;
        this.lookupService = lookupService;
    }

    public ValidationResult validate(Report report, String jurisdictionCode) {
        ValidationResult result = new ValidationResult();
        JurisdictionConfig jurisdiction = jurisdictionRegistry.find(jurisdictionCode).orElse(null);
        if (jurisdiction == null) {
            result.error("report", "UNKNOWN_JURISDICTION",
                    "Unknown jurisdiction '" + jurisdictionCode + "'");
            return result;
        }

        ReportCode code = report.getReportCode();
        validateHeader(report, jurisdiction, result);
        if (code == null) {
            // Shape rules below all key off the report code; nothing more to check.
            return result;
        }
        validateShape(report, code, result);
        validateIndicators(report, result);

        if (TRANSACTION_CODES.contains(code)) {
            validateTransactions(report, jurisdiction, result);
        } else if (ACTIVITY_CODES.contains(code)) {
            validateActivity(report, code, jurisdiction, result);
        }
        return result;
    }

    // ---------- header ----------

    private void validateHeader(Report report, JurisdictionConfig jurisdiction, ValidationResult result) {
        ReportCode code = report.getReportCode();

        if (report.getRentityId() == null || report.getRentityId() <= 0) {
            result.error("report.rentity_id", "MANDATORY", "rentity_id is mandatory and must be positive");
        }
        if (report.getSubmissionCode() == null) {
            result.error("report.submission_code", "MANDATORY", "submission_code is mandatory");
        }
        if (code == null) {
            result.error("report.report_code", "MANDATORY", "report_code is mandatory");
        } else if (!jurisdiction.allows(code)) {
            result.error("report.report_code", "REPORT_CODE_NOT_ALLOWED",
                    "report_code " + code + " is not accepted by jurisdiction " + jurisdiction.code());
        }

        if (isBlank(report.getEntityReference())) {
            result.error("report.entity_reference", "MANDATORY",
                    "entity_reference is mandatory (used as the per-tenant idempotency key)");
        } else if (report.getEntityReference().length() > MAX_ENTITY_REFERENCE) {
            result.error("report.entity_reference", "MAX_LENGTH",
                    "entity_reference exceeds " + MAX_ENTITY_REFERENCE + " characters");
        }

        if (report.getSubmissionDate() == null) {
            result.error("report.submission_date", "MANDATORY", "submission_date is mandatory");
        }

        if (isBlank(report.getCurrencyCodeLocal())) {
            result.error("report.currency_code_local", "MANDATORY", "currency_code_local is mandatory");
        } else if (!report.getCurrencyCodeLocal().equals(jurisdiction.defaultCurrency())) {
            result.error("report.currency_code_local", "CURRENCY_MISMATCH",
                    "currency_code_local must be " + jurisdiction.defaultCurrency()
                            + " for jurisdiction " + jurisdiction.code());
        }

        validateReportingPerson(report.getReportingPerson(), result);

        // Conditional: fiu_ref_number required for follow-up report types.
        if (code != null && FIU_REF_REQUIRED.contains(code) && isBlank(report.getFiuRefNumber())) {
            result.error("report.fiu_ref_number", "FIU_REF_REQUIRED",
                    "fiu_ref_number is mandatory for report_code " + code);
        }
        if (!isBlank(report.getFiuRefNumber()) && report.getFiuRefNumber().length() > MAX_FIU_REF) {
            result.error("report.fiu_ref_number", "MAX_LENGTH",
                    "fiu_ref_number exceeds " + MAX_FIU_REF + " characters");
        }

        // Conditional: location / reason / action required for STR / SAR.
        if (code != null && LOCATION_REASON_ACTION_REQUIRED.contains(code)) {
            if (report.getLocation() == null) {
                result.error("report.location", "MANDATORY",
                        "location is mandatory for report_code " + code);
            }
            if (isBlank(report.getReason())) {
                result.error("report.reason", "MANDATORY",
                        "reason is mandatory for report_code " + code);
            }
            if (isBlank(report.getAction())) {
                result.error("report.action", "MANDATORY",
                        "action is mandatory for report_code " + code);
            }
        }
        if (!isBlank(report.getReason()) && report.getReason().length() > MAX_REASON) {
            result.error("report.reason", "MAX_LENGTH", "reason exceeds " + MAX_REASON + " characters");
        }
        if (!isBlank(report.getAction()) && report.getAction().length() > MAX_ACTION) {
            result.error("report.action", "MAX_LENGTH", "action exceeds " + MAX_ACTION + " characters");
        }
    }

    private void validateReportingPerson(ReportingPerson person, ValidationResult result) {
        if (person == null) {
            result.error("report.reporting_person", "MANDATORY", "reporting_person is mandatory");
            return;
        }
        if (isBlank(person.getFirstName())) {
            result.error("report.reporting_person.first_name", "MANDATORY",
                    "reporting_person.first_name is mandatory");
        }
        if (isBlank(person.getLastName())) {
            result.error("report.reporting_person.last_name", "MANDATORY",
                    "reporting_person.last_name is mandatory");
        }
    }

    // ---------- shape (transaction XOR activity) ----------

    private void validateShape(Report report, ReportCode code, ValidationResult result) {
        boolean hasTransactions = report.getTransactions() != null && !report.getTransactions().isEmpty();
        boolean hasActivity = report.getActivity() != null;

        if (TRANSACTION_CODES.contains(code)) {
            if (!hasTransactions) {
                result.error("report.transaction", "SHAPE_REQUIRED",
                        "report_code " + code + " requires at least one transaction");
            }
            if (hasActivity) {
                result.error("report.activity", "SHAPE_CONFLICT",
                        "report_code " + code + " is transaction-based and must not carry an activity");
            }
        } else if (ACTIVITY_CODES.contains(code)) {
            if (!hasActivity) {
                result.error("report.activity", "SHAPE_REQUIRED",
                        "report_code " + code + " requires an activity");
            }
            if (hasTransactions) {
                result.error("report.transaction", "SHAPE_CONFLICT",
                        "report_code " + code + " is activity-based and must not carry transactions");
            }
        }
    }

    private void validateIndicators(Report report, ValidationResult result) {
        if (report.getReportIndicators() == null || report.getReportIndicators().isEmpty()) {
            result.error("report.report_indicators", "MANDATORY",
                    "at least one report indicator is mandatory");
        }
    }

    // ---------- transactions ----------

    private void validateTransactions(Report report, JurisdictionConfig jurisdiction, ValidationResult result) {
        List<Transaction> transactions = report.getTransactions();
        for (int i = 0; i < transactions.size(); i++) {
            Transaction tx = transactions.get(i);
            String path = "report.transaction[" + i + "]";

            if (isBlank(tx.getTransactionNumber())) {
                result.error(path + ".transactionnumber", "MANDATORY", "transactionnumber is mandatory");
            } else if (tx.getTransactionNumber().length() > MAX_TXN_NUMBER) {
                result.error(path + ".transactionnumber", "MAX_LENGTH",
                        "transactionnumber exceeds " + MAX_TXN_NUMBER + " characters");
            }
            if (tx.getDateTransaction() == null) {
                result.error(path + ".date_transaction", "MANDATORY", "date_transaction is mandatory");
            }
            if (tx.getAmountLocal() == null) {
                result.error(path + ".amount_local", "MANDATORY", "amount_local is mandatory");
            } else if (tx.getAmountLocal().compareTo(BigDecimal.ZERO) <= 0) {
                result.error(path + ".amount_local", "POSITIVE", "amount_local must be greater than zero");
            }

            validateTransModeLookup(tx, jurisdiction, path, result);
            validateTransactionParties(tx, path, result);
        }
    }

    private void validateTransModeLookup(Transaction tx, JurisdictionConfig jurisdiction, String path,
                                         ValidationResult result) {
        String transmode = tx.getTransmodeCode();
        if (isBlank(transmode)) {
            result.error(path + ".transmode_code", "MANDATORY", "transmode_code is mandatory");
            return;
        }
        if (lookupService.hasSet(jurisdiction.lookupSet(), "transmode")
                && !lookupService.isValid(jurisdiction.lookupSet(), "transmode", transmode)) {
            result.error(path + ".transmode_code", "LOOKUP",
                    "transmode_code '" + transmode + "' is not in the " + jurisdiction.lookupSet()
                            + " transmode lookup");
        }
    }

    /**
     * A transaction is either <strong>bi-party</strong> (exactly one from-side and one to-side, no
     * t_party) or <strong>multi-party</strong> (one or more t_party, each with exactly one subject,
     * and no from/to sides).
     */
    private void validateTransactionParties(Transaction tx, String path, ValidationResult result) {
        int fromSides = (tx.getTFrom() != null ? 1 : 0) + (tx.getTFromMyClient() != null ? 1 : 0);
        int toSides = (tx.getTTo() != null ? 1 : 0) + (tx.getTToMyClient() != null ? 1 : 0);
        boolean hasBiParty = fromSides > 0 || toSides > 0;
        boolean hasMultiParty = tx.getTParties() != null && !tx.getTParties().isEmpty();

        if (hasBiParty && hasMultiParty) {
            result.error(path, "PARTY_SHAPE_CONFLICT",
                    "transaction must be bi-party (t_from*/t_to*) OR multi-party (t_party), not both");
            return;
        }
        if (!hasBiParty && !hasMultiParty) {
            result.error(path, "PARTY_REQUIRED",
                    "transaction must have either bi-party sides (t_from*/t_to*) or t_party entries");
            return;
        }

        if (hasBiParty) {
            if (fromSides != 1) {
                result.error(path, "BIPARTY_FROM",
                        "bi-party transaction must have exactly one from-side (t_from or t_from_my_client)");
            }
            if (toSides != 1) {
                result.error(path, "BIPARTY_TO",
                        "bi-party transaction must have exactly one to-side (t_to or t_to_my_client)");
            }
        } else {
            List<TParty> parties = tx.getTParties();
            for (int j = 0; j < parties.size(); j++) {
                validateTPartySubject(parties.get(j), path + ".t_party[" + j + "]", result);
            }
        }
    }

    private void validateTPartySubject(TParty party, String path, ValidationResult result) {
        int subjects = count(party.getPerson(), party.getPersonMyClient(),
                party.getAccount(), party.getAccountMyClient(),
                party.getEntity(), party.getEntityMyClient());
        if (subjects != 1) {
            result.error(path, "PARTY_SUBJECT",
                    "t_party must have exactly one subject (person/account/entity, plain or my_client), found "
                            + subjects);
        }
        if (isBlank(party.getRole())) {
            result.error(path + ".role", "MANDATORY", "t_party.role is mandatory");
        }
    }

    // ---------- activity ----------

    private void validateActivity(Report report, ReportCode code, JurisdictionConfig jurisdiction,
                                  ValidationResult result) {
        Activity activity = report.getActivity();
        if (activity == null) {
            return; // already reported by validateShape
        }
        List<ReportParty> parties = activity.getReportParties();
        if (parties == null || parties.isEmpty()) {
            result.error("report.activity.report_parties", "MANDATORY",
                    "activity requires at least one report_party");
        } else {
            for (int i = 0; i < parties.size(); i++) {
                validateReportPartySubject(parties.get(i), "report.activity.report_party[" + i + "]", result);
            }
        }

        if (code == ReportCode.DPMSR) {
            validateDpms(activity, jurisdiction, result);
        }
    }

    private void validateReportPartySubject(ReportParty party, String path, ValidationResult result) {
        int subjects = count(party.getPerson(), party.getPersonMyClient());
        if (subjects != 1) {
            result.error(path, "PARTY_SUBJECT",
                    "report_party must have exactly one subject (person or person_my_client), found " + subjects);
        }
    }

    /**
     * UAE DPMS rules: at least one goods_services line, and the total estimated value in AED must
     * meet the reporting threshold (AED 55,000). goods_services priced in a non-default currency are
     * not summed (we cannot convert without an FX rate) and instead raise a warning.
     */
    private void validateDpms(Activity activity, JurisdictionConfig jurisdiction, ValidationResult result) {
        List<GoodsServices> goods = activity.getGoodsServices();
        if (goods == null || goods.isEmpty()) {
            result.error("report.activity.goods_services", "DPMS_GOODS_REQUIRED",
                    "DPMSR requires at least one goods_services line");
            return;
        }

        BigDecimal threshold = jurisdiction.dpmsThreshold();
        BigDecimal localTotal = BigDecimal.ZERO;
        boolean sawNonLocalCurrency = false;

        for (int i = 0; i < goods.size(); i++) {
            GoodsServices g = goods.get(i);
            String path = "report.activity.goods_services[" + i + "]";
            if (isBlank(g.getItemType())) {
                result.error(path + ".item_type", "MANDATORY", "goods_services.item_type is mandatory");
            }
            if (g.getEstimatedValue() == null) {
                result.error(path + ".estimated_value", "MANDATORY",
                        "goods_services.estimated_value is mandatory for DPMSR");
                continue;
            }
            if (!isBlank(g.getCurrencyCode())
                    && lookupService.hasSet(jurisdiction.lookupSet(), "currencies")
                    && !lookupService.isValid(jurisdiction.lookupSet(), "currencies", g.getCurrencyCode())) {
                result.error(path + ".currency_code", "LOOKUP",
                        "currency_code '" + g.getCurrencyCode() + "' is not in the currencies lookup");
            }
            if (g.getCurrencyCode() == null || g.getCurrencyCode().equals(jurisdiction.defaultCurrency())) {
                localTotal = localTotal.add(g.getEstimatedValue());
            } else {
                sawNonLocalCurrency = true;
            }
        }

        if (threshold != null && localTotal.compareTo(threshold) < 0) {
            if (sawNonLocalCurrency) {
                result.warning("report.activity.goods_services", "DPMS_THRESHOLD_FX",
                        "goods priced in a non-" + jurisdiction.defaultCurrency()
                                + " currency cannot be summed; verify the AED 55,000 threshold manually");
            } else {
                result.error("report.activity.goods_services", "DPMS_THRESHOLD",
                        "total estimated value " + localTotal + " " + jurisdiction.defaultCurrency()
                                + " is below the DPMS reporting threshold of " + threshold);
            }
        }
    }

    // ---------- helpers ----------

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static int count(Object... values) {
        int n = 0;
        for (Object v : values) {
            if (v != null) {
                n++;
            }
        }
        return n;
    }
}
