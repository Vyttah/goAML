package com.vyttah.goaml.engine.build;

import java.util.regex.Pattern;

/**
 * Normalizes person/entity name strings to the goAML XSD name pattern {@code [a-zA-Z0-9 .'-]*} (C8).
 *
 * <p>UAE legal and personal names routinely contain {@code &}, {@code ,}, {@code (}, {@code )} and {@code /}
 * — none allowed by the schema pattern (see {@code goAMLSchema.xsd} {@code <xs:pattern value="[a-zA-Z0-9 .'-]*"/>}
 * on {@code first_name}, {@code last_name}, entity {@code name}, …). Left unmapped these fail marshalling/XSD
 * validation with a raw, unhelpful SAX message. This helper makes the <em>common, safe</em> substitutions so
 * the report stays XSD-valid without losing meaning:
 * <ul>
 *   <li>{@code &} → {@code and} (the dominant case: "GOLD &amp; DIAMONDS" → "GOLD and DIAMONDS")</li>
 *   <li>{@code /} → space (e.g. "A/C" → "A C")</li>
 *   <li>{@code (} {@code )} {@code ,} → removed, surrounding spaces collapsed</li>
 * </ul>
 * and collapses any resulting double spaces. Characters it cannot safely map (most importantly <b>Arabic
 * script</b> and other non-Latin letters) are left in place — the report then fails the pattern, and
 * {@link #matchesPattern(String)} lets the validator raise a <em>clear, specific</em> PATTERN error
 * (rather than a raw SAX line/column) so a caller knows to supply a Latin transliteration.
 */
public final class NameNormalizer {

    /** The goAML schema name pattern (kept identical to the XSD facet). */
    public static final String NAME_PATTERN = "[a-zA-Z0-9 .'-]*";

    private static final Pattern COMPILED = Pattern.compile(NAME_PATTERN);
    private static final Pattern MULTI_SPACE = Pattern.compile("\\s{2,}");

    private NameNormalizer() {
    }

    /**
     * Returns the name with the common XSD-illegal punctuation safely substituted/removed. {@code null} in →
     * {@code null} out; blank stays blank. Does not touch characters it cannot map (Arabic etc.) — callers
     * should follow with {@link #matchesPattern(String)} to detect a still-invalid result.
     */
    public static String normalize(String name) {
        if (name == null) {
            return null;
        }
        String out = name
                .replace("&", " and ")
                .replace("/", " ")
                .replace("(", " ")
                .replace(")", " ")
                .replace(",", " ");
        out = MULTI_SPACE.matcher(out).replaceAll(" ").trim();
        return out;
    }

    /** True when the value is null/empty or fully matches the goAML name pattern. */
    public static boolean matchesPattern(String name) {
        return name == null || name.isEmpty() || COMPILED.matcher(name).matches();
    }
}
