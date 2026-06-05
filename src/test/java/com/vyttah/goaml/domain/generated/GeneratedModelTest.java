package com.vyttah.goaml.domain.generated;

import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Step 2 checkpoint: the xjc-generated JAXB model (from goAMLSchema.xsd 5.0.2) is present, usable, and
 * bound as expected — root POJO instantiates, the report-type enum carries the UAE codes, and date types
 * are mapped to {@link OffsetDateTime} (via GoamlDateTimeAdapter), not XMLGregorianCalendar. The real
 * proof (round-tripping the actual portal samples through this model) is Step 3.
 */
class GeneratedModelTest {

    @Test
    void generatedRootAndFactoryAreUsable() {
        assertThat(new Report()).isNotNull();
        assertThat(new ObjectFactory().createReport()).isNotNull();
    }

    @Test
    void reportTypeEnumCarriesTheUaeCodes() {
        assertThat(ReportType.DPMSR).isNotNull();
        assertThat(Arrays.stream(ReportType.values()).map(Enum::name))
                .contains("DPMSR", "STR", "SAR", "REAR", "PNMRA", "CNMRA", "AIF", "ECDD");
    }

    @Test
    void datesAreBoundToOffsetDateTime() {
        // t_trans_item carries registration_date; the global binding maps sql_date -> OffsetDateTime.
        boolean hasOffsetDateTimeField = Arrays.stream(TTransItem.class.getDeclaredFields())
                .anyMatch(f -> f.getType() == OffsetDateTime.class);
        assertThat(hasOffsetDateTimeField)
                .as("generated TTransItem should bind a date field to OffsetDateTime")
                .isTrue();
    }
}
