package com.vyttah.goaml.engine.validation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Step 1 anchor test: the two real DPMSR sample reports (exported from the live UAE goAML portal)
 * must validate clean against the authoritative {@code goAMLSchema.xsd} (5.0.2). This proves the
 * schema and the samples agree before we generate any Java from the schema.
 */
class GoamlXsdValidationTest {

    private final XsdSchemaValidator validator = new XsdSchemaValidator();

    @ParameterizedTest
    @ValueSource(strings = {
            "samples/TR.2079.200000309.xml",
            "samples/TR.2079.200000310.xml"
    })
    void realDpmsrSamplesValidateAgainstSchema(String resource) throws IOException {
        byte[] xml = readResource(resource);

        ValidationResult result = validator.validate(xml);

        assertThat(result.isValid())
                .as("real sample %s should conform to goAMLSchema.xsd; errors=%s", resource, result.errors())
                .isTrue();
    }

    @Test
    void rejectsStructurallyInvalidReport() {
        byte[] bad = """
                <?xml version="1.0" encoding="utf-8"?>
                <report>
                    <not_a_real_element>x</not_a_real_element>
                </report>
                """.getBytes(StandardCharsets.UTF_8);

        ValidationResult result = validator.validate(bad);

        assertThat(result.isValid()).isFalse();
        assertThat(result.errors()).isNotEmpty();
        assertThat(result.hasCode("XSD")).isTrue();
    }

    private static byte[] readResource(String path) throws IOException {
        try (InputStream in = Thread.currentThread().getContextClassLoader().getResourceAsStream(path)) {
            assertThat(in).as("test resource %s present on classpath", path).isNotNull();
            return in.readAllBytes();
        }
    }
}
