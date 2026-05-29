package com.vyttah.goaml.domain.enums;

/**
 * goAML report types. {@code STR/SAR/AIF/AIFT/ECDD/ECDDT} are from schema v4.0;
 * {@code DPMSR} is the UAE FIU-defined code for Dealers in Precious Metals and Stones.
 * JAXB serializes each value by its enum name, which matches the wire format.
 */
public enum ReportCode {
    STR, SAR, AIF, AIFT, ECDD, ECDDT, DPMSR;
}
