package com.vyttah.goaml.engine.build;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * C8 — the name normalizer maps the common XSD-illegal punctuation to a valid form and flags truly
 * unmappable (non-Latin) input.
 */
class NameNormalizerTest {

    @Test
    void ampersandBecomesAndAndResultMatchesThePattern() {
        String out = NameNormalizer.normalize("GOLD & DIAMONDS L.L.C");
        assertThat(out).isEqualTo("GOLD and DIAMONDS L.L.C");
        assertThat(NameNormalizer.matchesPattern(out)).isTrue();
    }

    @Test
    void bracketsCommasAndSlashesAreStrippedOrReplacedAndCollapse() {
        assertThat(NameNormalizer.normalize("ACME (DUBAI), TRADING / FZE"))
                .isEqualTo("ACME DUBAI TRADING FZE");
        assertThat(NameNormalizer.matchesPattern(NameNormalizer.normalize("ACME (DUBAI), TRADING / FZE")))
                .isTrue();
    }

    @Test
    void plainNameIsUnchangedAndNullStaysNull() {
        assertThat(NameNormalizer.normalize("O'Brien-Smith")).isEqualTo("O'Brien-Smith");
        assertThat(NameNormalizer.normalize(null)).isNull();
    }

    @Test
    void arabicScriptCannotBeMappedAndFailsThePattern() {
        // normalization leaves non-Latin letters in place → still pattern-invalid (validator raises a clear error)
        String arabic = "محمد"; // محمد
        assertThat(NameNormalizer.matchesPattern(NameNormalizer.normalize(arabic))).isFalse();
        assertThat(NameNormalizer.matchesPattern("Mohamed")).isTrue();
    }
}
