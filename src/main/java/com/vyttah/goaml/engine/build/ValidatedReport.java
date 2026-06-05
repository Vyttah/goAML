package com.vyttah.goaml.engine.build;

import com.vyttah.goaml.domain.generated.Report;
import com.vyttah.goaml.engine.validation.ValidationResult;

/**
 * A built {@link Report}, its marshalled goAML {@link #xml} (computed once, reused for the XSD gate and for
 * persistence), and both validation verdicts: the business-rule result
 * ({@link com.vyttah.goaml.engine.validation.ReportValidator}) and the authoritative XSD result
 * ({@link com.vyttah.goaml.engine.validation.XsdSchemaValidator}). {@link #isValid()} is true only when both
 * pass.
 *
 * <p>Carrying the XML here lets callers persist the report without ever importing the JAXB {@code Report}
 * type (keeping the persistence aggregate's name unambiguous).
 */
public record ValidatedReport(Report report, String xml, ValidationResult rules, ValidationResult xsd) {

    public boolean isValid() {
        return rules.isValid() && xsd.isValid();
    }
}
