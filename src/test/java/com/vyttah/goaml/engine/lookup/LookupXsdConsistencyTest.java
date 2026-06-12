package com.vyttah.goaml.engine.lookup;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Durable invariant guarding the lookup ↔ XSD-enum relationship. goAML has two parallel sources of allowed
 * values: the authoritative XSD enumerations (structural) and the per-jurisdiction lookup JSONs (business-rule
 * validation). A lookup the {@code ReportValidator} checks MUST be a subset of its XSD enumeration — otherwise
 * a validator-clean report can still fail the schema (exactly the {@code transmode} drift fixed in Step 5).
 *
 * <p>This test fails fast if anyone adds a lookup code the schema won't accept. Pure file/parsing — no Spring,
 * no Docker.
 */
class LookupXsdConsistencyTest {

    private static final String SCHEMA = "xsd/goaml/5.0.2/goAMLSchema.xsd";

    @Test
    void transmodeLookupIsSubsetOfConductionType() throws Exception {
        assertLookupSubsetOfEnum("lookups/ae/transmode.json", "conduction_type");
    }

    @Test
    void currenciesLookupIsSubsetOfCurrencyType() throws Exception {
        assertLookupSubsetOfEnum("lookups/ae/currencies.json", "currency_type");
    }

    @Test
    void itemTypesLookupIsSubsetOfTransItemType() throws Exception {
        assertLookupSubsetOfEnum("lookups/ae/item_types.json", "trans_item_type");
    }

    @Test
    void itemStatusLookupIsSubsetOfTransItemStatus() throws Exception {
        assertLookupSubsetOfEnum("lookups/ae/item_status.json", "trans_item_status");
    }

    @Test
    void reportIndicatorsLookupIsSubsetOfReportIndicatorType() throws Exception {
        assertLookupSubsetOfEnum("lookups/ae/report_indicators.json", "report_indicator_type");
    }

    /**
     * The country lookup is the cockpit/feed's authoritative resolver for nationality / residence / address /
     * identification country codes — an entry the XSD won't accept is a silent field-omission risk in filed XML
     * (the {@code country_type} fields are optional on the lenient subjects, so the XSD gate can't catch the
     * loss). Pin {@code countries.json} as a subset of the XSD {@code country_type} enum so the regenerated file
     * can never silently drift (audit finding A1).
     */
    @Test
    void countriesLookupIsSubsetOfCountryType() throws Exception {
        assertLookupSubsetOfEnum("lookups/ae/countries.json", "country_type");
    }

    private void assertLookupSubsetOfEnum(String lookupResource, String simpleTypeName) throws Exception {
        Set<String> codes = lookupCodes(lookupResource);
        Set<String> enumValues = xsdEnumValues(simpleTypeName);

        assertThat(enumValues)
                .as("XSD simpleType '%s' should declare enumerations", simpleTypeName)
                .isNotEmpty();
        assertThat(codes).as("lookup '%s' should not be empty", lookupResource).isNotEmpty();
        assertThat(enumValues)
                .as("every code in %s must be a member of XSD enum '%s' (lookup ⊆ XSD); offenders: %s",
                        lookupResource, simpleTypeName, minus(codes, enumValues))
                .containsAll(codes);
    }

    private static Set<String> minus(Set<String> a, Set<String> b) {
        Set<String> diff = new HashSet<>(a);
        diff.removeAll(b);
        return diff;
    }

    private Set<String> lookupCodes(String resource) throws Exception {
        try (InputStream in = cl().getResourceAsStream(resource)) {
            assertThat(in).as("lookup resource %s on classpath", resource).isNotNull();
            JsonNode array = new ObjectMapper().readTree(in);
            Set<String> codes = new HashSet<>();
            array.forEach(node -> codes.add(node.get("code").asText()));
            return codes;
        }
    }

    private Set<String> xsdEnumValues(String simpleTypeName) throws Exception {
        Document doc;
        try (InputStream in = cl().getResourceAsStream(SCHEMA)) {
            assertThat(in).as("schema %s on classpath", SCHEMA).isNotNull();
            doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(in);
        }
        NodeList simpleTypes = doc.getElementsByTagName("xs:simpleType");
        for (int i = 0; i < simpleTypes.getLength(); i++) {
            Element type = (Element) simpleTypes.item(i);
            if (simpleTypeName.equals(type.getAttribute("name"))) {
                Set<String> values = new HashSet<>();
                NodeList enums = type.getElementsByTagName("xs:enumeration");
                for (int j = 0; j < enums.getLength(); j++) {
                    values.add(((Element) enums.item(j)).getAttribute("value"));
                }
                return values;
            }
        }
        return Set.of();
    }

    private static ClassLoader cl() {
        return Thread.currentThread().getContextClassLoader();
    }
}
