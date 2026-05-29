package com.vyttah.goaml.engine;

import com.vyttah.goaml.domain.Report;
import com.vyttah.goaml.domain.enums.ReportCode;
import com.vyttah.goaml.engine.build.ActivityReportBuilder;
import com.vyttah.goaml.engine.build.TransactionReportBuilder;
import com.vyttah.goaml.engine.marshal.ReportMarshaller;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.xmlunit.builder.DiffBuilder;
import org.xmlunit.builder.Input;
import org.xmlunit.diff.Diff;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Parameterized regression check: for every {@link ReportCode}, the engine's marshalled XML
 * must equal the committed golden file under {@code src/test/resources/golden/<code>.xml}.
 *
 * <p>Bootstrap / regeneration: run with {@code -Dgoaml.golden.regenerate=true} and the test
 * will overwrite the golden files with the current engine output instead of comparing. Use
 * this any time the schema/sample fixtures intentionally change.
 */
class EngineGoldenTest {

    private static final boolean REGENERATE =
            Boolean.parseBoolean(System.getProperty("goaml.golden.regenerate", "false"));
    private static final Path GOLDEN_DIR = Paths.get("src/test/resources/golden");

    private final ReportMarshaller marshaller = new ReportMarshaller();
    private final ActivityReportBuilder activityBuilder = new ActivityReportBuilder();
    private final TransactionReportBuilder transactionBuilder = new TransactionReportBuilder();

    @ParameterizedTest
    @EnumSource(ReportCode.class)
    void engineOutputMatchesGoldenForEveryReportType(ReportCode code) throws IOException {
        byte[] actualXml = marshalSample(code);
        Path goldenPath = GOLDEN_DIR.resolve(code.name() + ".xml");

        if (REGENERATE) {
            Files.createDirectories(GOLDEN_DIR);
            Files.write(goldenPath, actualXml);
            return;
        }

        assertThat(Files.exists(goldenPath))
                .as("Golden file missing for %s — run with -Dgoaml.golden.regenerate=true to bootstrap",
                        code.name())
                .isTrue();

        byte[] expectedXml = Files.readAllBytes(goldenPath);
        Diff diff = DiffBuilder.compare(Input.fromByteArray(expectedXml))
                .withTest(Input.fromByteArray(actualXml))
                .ignoreWhitespace()
                .ignoreComments()
                .checkForSimilar()
                .build();

        assertThat(diff.hasDifferences())
                .as("Engine output for %s drifted from golden:%n%s%n--- actual ---%n%s",
                        code.name(), diff.toString(), new String(actualXml))
                .isFalse();
    }

    private byte[] marshalSample(ReportCode code) {
        SampleReports.Sample sample = SampleReports.sampleFor(code);
        Report report = sample.isActivity()
                ? activityBuilder.build(sample.header(), sample.activity())
                : transactionBuilder.build(sample.header(), sample.transactions());
        return marshaller.marshal(report);
    }
}
