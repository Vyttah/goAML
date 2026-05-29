package com.vyttah.goaml.domain.common;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlValue;

/**
 * One {@code <indicator>} child of the report's {@code <report_indicators>} block.
 * Carries a single string code as its element text content (e.g. {@code DPMSR_CASH_THRESHOLD}).
 * Concrete codes come from the FIU's lookup tables (Phase 5).
 */
@XmlAccessorType(XmlAccessType.FIELD)
public class ReportIndicator {

    @XmlValue
    private String value;

    public ReportIndicator() {}
    public ReportIndicator(String value) { this.value = value; }

    public String getValue() { return value; }
    public void setValue(String value) { this.value = value; }
}
