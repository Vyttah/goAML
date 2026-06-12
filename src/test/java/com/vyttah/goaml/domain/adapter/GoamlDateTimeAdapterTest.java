package com.vyttah.goaml.domain.adapter;

import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The goAML {@code dateTime} format carries no zone, so the adapter must preserve the caller's
 * <em>wall-clock</em> date/time — a {@code +04:00} (UAE) local-midnight birthdate filed as UTC would land on
 * the PREVIOUS calendar day at the FIU.
 */
class GoamlDateTimeAdapterTest {

    private final GoamlDateTimeAdapter adapter = new GoamlDateTimeAdapter();

    @Test
    void marshalsAtTheOriginalOffsetNotUtc() {
        OffsetDateTime uaeMidnight = OffsetDateTime.parse("1990-03-12T00:00:00+04:00");
        assertThat(adapter.marshal(uaeMidnight)).isEqualTo("1990-03-12T00:00:00");
    }

    @Test
    void marshalsUtcValuesUnchanged() {
        OffsetDateTime utc = OffsetDateTime.of(2026, 5, 26, 9, 0, 0, 0, ZoneOffset.UTC);
        assertThat(adapter.marshal(utc)).isEqualTo("2026-05-26T09:00:00");
    }

    @Test
    void marshalsNegativeOffsetWallClockToo() {
        OffsetDateTime ny = OffsetDateTime.parse("2026-01-01T23:30:00-05:00");
        assertThat(adapter.marshal(ny)).isEqualTo("2026-01-01T23:30:00");
    }

    @Test
    void unmarshalParsesTheWallClock() {
        OffsetDateTime parsed = adapter.unmarshal("1990-03-12T00:00:00");
        assertThat(parsed.toLocalDateTime()).isEqualTo("1990-03-12T00:00:00");
    }

    @Test
    void wallClockSurvivesAMarshalUnmarshalRoundTrip() {
        OffsetDateTime original = OffsetDateTime.parse("2026-06-11T00:00:00+04:00");
        OffsetDateTime roundTripped = adapter.unmarshal(adapter.marshal(original));
        assertThat(roundTripped.toLocalDateTime()).isEqualTo(original.toLocalDateTime());
    }

    @Test
    void nullsPassThrough() {
        assertThat(adapter.marshal(null)).isNull();
        assertThat(adapter.unmarshal(null)).isNull();
    }
}
