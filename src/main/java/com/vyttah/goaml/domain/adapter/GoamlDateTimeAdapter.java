package com.vyttah.goaml.domain.adapter;

import jakarta.xml.bind.annotation.adapters.XmlAdapter;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * Marshals {@link OffsetDateTime} ↔ goAML's strict {@code YYYY-MM-DDTHH:MM:SS} format
 * (no timezone suffix, second-precision).
 *
 * <p><strong>Marshal preserves the caller's wall-clock</strong>: the value is formatted at its
 * <em>original</em> offset, never normalized to UTC. goAML dates are calendar facts (a birthdate, the
 * submission day) — converting a {@code +04:00} local midnight to UTC would file the <em>previous</em>
 * calendar date with the FIU. Unmarshal has no offset information in the XML, so the parsed local
 * date-time is tagged UTC by convention (the wall-clock fields are what round-trip).
 */
public final class GoamlDateTimeAdapter extends XmlAdapter<String, OffsetDateTime> {

    private static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    @Override
    public OffsetDateTime unmarshal(String value) {
        return value == null ? null : LocalDateTime.parse(value, FORMAT).atOffset(ZoneOffset.UTC);
    }

    @Override
    public String marshal(OffsetDateTime value) {
        return value == null ? null : value.toLocalDateTime().format(FORMAT);
    }
}
