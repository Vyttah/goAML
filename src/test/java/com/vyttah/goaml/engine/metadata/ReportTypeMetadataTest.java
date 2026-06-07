package com.vyttah.goaml.engine.metadata;

import com.vyttah.goaml.domain.generated.ReportType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit test for {@link ReportTypeMetadata} — the single source of truth for report shape + conditional-field
 * requirements (shared by the validator and the MCP describe tool).
 */
class ReportTypeMetadataTest {

    @Test
    void shapeClassification() {
        assertThat(ReportTypeMetadata.shapeOf(ReportType.STR)).isEqualTo(ReportTypeMetadata.Shape.TRANSACTION);
        assertThat(ReportTypeMetadata.shapeOf(ReportType.DPMSR)).isEqualTo(ReportTypeMetadata.Shape.ACTIVITY);
        assertThat(ReportTypeMetadata.shapeOf(ReportType.HRC)).isEqualTo(ReportTypeMetadata.Shape.OTHER);
        assertThat(ReportTypeMetadata.isTransactionShaped(ReportType.AIFT)).isTrue();
        assertThat(ReportTypeMetadata.isActivityShaped(ReportType.SAR)).isTrue();
    }

    @Test
    void conditionalRequirements() {
        assertThat(ReportTypeMetadata.requiresFiuRef(ReportType.AIF)).isTrue();
        assertThat(ReportTypeMetadata.requiresFiuRef(ReportType.DPMSR)).isFalse();
        assertThat(ReportTypeMetadata.requiresLocationReasonAction(ReportType.STR)).isTrue();
        assertThat(ReportTypeMetadata.requiresLocationReasonAction(ReportType.DPMSR)).isFalse();
    }

    @Test
    void describeIsFlatAndComplete() {
        ReportTypeMetadata.Descriptor d = ReportTypeMetadata.describe(ReportType.DPMSR);

        assertThat(d.code()).isEqualTo("DPMSR");
        assertThat(d.shape()).isEqualTo("ACTIVITY");
        assertThat(d.fiuRefRequired()).isFalse();
        assertThat(d.locationReasonActionRequired()).isFalse();
    }
}
