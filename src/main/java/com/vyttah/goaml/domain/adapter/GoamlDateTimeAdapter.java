package com.vyttah.goaml.domain.adapter;

import jakarta.xml.bind.annotation.adapters.XmlAdapter;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * Marshals {@link OffsetDateTime} ↔ goAML's strict {@code YYYY-MM-DDTHH:MM:SS} format
 * (no timezone suffix, second-precision). Values are normalized to UTC on marshal.
 */
public final class GoamlDateTimeAdapter extends XmlAdapter<String, OffsetDateTime> {

    private static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    @Override
    public OffsetDateTime unmarshal(String value) {
        return value == null ? null : LocalDateTime.parse(value, FORMAT).atOffset(ZoneOffset.UTC);
    }

    @Override
    public String marshal(OffsetDateTime value) {
        return value == null ? null : value.withOffsetSameInstant(ZoneOffset.UTC).toLocalDateTime().format(FORMAT);
    }
}
