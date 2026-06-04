package com.vyttah.goaml.engine.build;

import com.vyttah.goaml.domain.generated.Report;
import com.vyttah.goaml.engine.validation.ValidationResult;

/**
 * A built {@link Report} together with both validation verdicts: the business-rule result
 * ({@link com.vyttah.goaml.engine.validation.ReportValidator}) and the authoritative XSD result
 * ({@link com.vyttah.goaml.engine.validation.XsdSchemaValidator}). {@link #isValid()} is true only when both pass.
 */
public record ValidatedReport(Report report, ValidationResult rules, ValidationResult xsd) {

    public boolean isValid() {
        return rules.isValid() && xsd.isValid();
    }
}
