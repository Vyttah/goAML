package com.vyttah.goaml.engine;

import com.vyttah.goaml.domain.generated.Report;
import com.vyttah.goaml.domain.generated.ReportType;
import com.vyttah.goaml.engine.build.ActivityReportBuilder;
import com.vyttah.goaml.engine.build.TransactionReportBuilder;
import com.vyttah.goaml.engine.marshal.ReportMarshaller;
import com.vyttah.goaml.engine.validation.ValidationResult;
import com.vyttah.goaml.engine.validation.XsdSchemaValidator;
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
 * Parameterized regression check: for every report type that {@link SampleReports} covers, the engine's
 * marshalled XML must (a) conform to the authoritative {@code goAMLSchema.xsd} and (b) equal the committed
 * golden file under {@code src/test/resources/golden/<code>.xml}.
 *
 * <p>Bootstrap / regeneration: run with {@code -Dgoaml.golden.regenerate=true} and the test will overwrite
 * the golden files with the current engine output instead of comparing. The XSD conformance check still runs
 * during regeneration, so a malformed sample can never be blessed as a golden.
 */
class EngineGoldenTest {

    // Golden coverage = the activity-shaped report types (DPMSR-first). The transaction-shaped types
    // (STR/AIFT/ECDDT) are deferred to Step 5: their goldens need the `transmode.json` lookup reconciled
    // with the goAML XSD `transmode_type` enumeration (the two are currently disjoint — see STEP-4 doc).
    // Validator-level rule coverage for the transaction types is retained in ReportValidatorTest.
    private static final boolean REGENERATE =
            Boolean.parseBoolean(System.getProperty("goaml.golden.regenerate", "false"));
    private static final Path GOLDEN_DIR = Paths.get("src/test/resources/golden");

    private final ReportMarshaller marshaller = new ReportMarshaller();
    private final ActivityReportBuilder activityBuilder = new ActivityReportBuilder();
    private final TransactionReportBuilder transactionBuilder = new TransactionReportBuilder();
    private final XsdSchemaValidator xsdValidator = new XsdSchemaValidator();

    @ParameterizedTest
    @EnumSource(value = ReportType.class, names = {"DPMSR", "SAR", "AIF", "ECDD"})
    void engineOutputMatchesGoldenForEveryReportType(ReportType code) throws IOException {
        byte[] actualXml = marshalSample(code);

        // The engine's output must always conform to the authoritative schema — even when regenerating.
        ValidationResult xsd = xsdValidator.validate(actualXml);
        assertThat(xsd.isValid())
                .as("engine output for %s must conform to goAMLSchema.xsd; errors=%s", code, xsd.errors())
                .isTrue();

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

    private byte[] marshalSample(ReportType code) {
        SampleReports.Sample sample = SampleReports.sampleFor(code);
        Report report = sample.isActivity()
                ? activityBuilder.build(sample.header(), sample.activity())
                : transactionBuilder.build(sample.header(), sample.transactions());
        return marshaller.marshal(report);
    }
}
