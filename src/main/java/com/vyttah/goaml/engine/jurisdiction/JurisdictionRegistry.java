package com.vyttah.goaml.engine.jurisdiction;

import com.vyttah.goaml.domain.enums.ReportCode;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Loads and caches {@link JurisdictionConfig} from {@code classpath:jurisdictions/*.yml} at
 * startup. Lookups are case-insensitive on the jurisdiction code.
 */
@Component
public class JurisdictionRegistry {

    private static final String LOCATION_PATTERN = "classpath*:jurisdictions/*.yml";

    private final Map<String, JurisdictionConfig> byCode = new ConcurrentHashMap<>();

    public JurisdictionRegistry() {
        load();
    }

    private void load() {
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        Resource[] resources;
        try {
            resources = resolver.getResources(LOCATION_PATTERN);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to scan jurisdiction configs at " + LOCATION_PATTERN, e);
        }
        Yaml yaml = new Yaml();
        for (Resource resource : resources) {
            try (InputStream in = resource.getInputStream()) {
                Map<String, Object> raw = yaml.load(in);
                if (raw == null) {
                    continue;
                }
                JurisdictionConfig config = parse(raw, resource.getFilename());
                byCode.put(config.code().toLowerCase(Locale.ROOT), config);
            } catch (IOException e) {
                throw new IllegalStateException("Failed to read jurisdiction config " + resource.getFilename(), e);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private JurisdictionConfig parse(Map<String, Object> raw, String filename) {
        String code = requireString(raw, "code", filename);
        String name = requireString(raw, "name", filename);
        String currency = requireString(raw, "defaultCurrency", filename);
        String lookupSet = Objects.toString(raw.getOrDefault("lookupSet", code), code);

        Object thresholdRaw = raw.get("dpmsThreshold");
        BigDecimal threshold = thresholdRaw == null ? null : new BigDecimal(thresholdRaw.toString());

        Object codesRaw = raw.get("allowedReportCodes");
        if (!(codesRaw instanceof List<?> codeList) || codeList.isEmpty()) {
            throw new IllegalStateException("Jurisdiction config " + filename + " missing 'allowedReportCodes'");
        }
        Set<ReportCode> codes = new LinkedHashSet<>();
        for (Object c : codeList) {
            codes.add(ReportCode.valueOf(c.toString().trim().toUpperCase(Locale.ROOT)));
        }
        return new JurisdictionConfig(code, name, currency, codes, threshold, lookupSet);
    }

    private String requireString(Map<String, Object> raw, String key, String filename) {
        Object v = raw.get(key);
        if (v == null || v.toString().isBlank()) {
            throw new IllegalStateException("Jurisdiction config " + filename + " missing '" + key + "'");
        }
        return v.toString().trim();
    }

    public Optional<JurisdictionConfig> find(String code) {
        if (code == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(byCode.get(code.toLowerCase(Locale.ROOT)));
    }

    public JurisdictionConfig require(String code) {
        return find(code).orElseThrow(() ->
                new IllegalArgumentException("Unknown jurisdiction: " + code));
    }

    public Set<String> codes() {
        return Set.copyOf(byCode.keySet());
    }
}
