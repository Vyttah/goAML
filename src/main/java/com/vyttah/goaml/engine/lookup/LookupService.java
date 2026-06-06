package com.vyttah.goaml.engine.lookup;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Seeds and serves FIU-defined lookup code sets from {@code classpath:lookups/<jurisdiction>/<set>.json}.
 * Each file is a JSON array, either of bare strings ({@code ["CASH","WIRE"]}) or of objects with a
 * {@code "code"} field ({@code [{"code":"CASH","label":"Cash"}]}).
 *
 * <p>These seeds are placeholders pending the authoritative UAE lookup exports (see plan Open Item #4);
 * they are refreshed at runtime from the goAML {@code OdataLookups} endpoint in a later phase. Validation
 * treats an <em>absent</em> set as "cannot check" (skipped), and an absent <em>code within a loaded set</em>
 * as invalid.
 */
@Component
public class LookupService {

    private static final String LOCATION_PATTERN = "classpath*:lookups/*/*.json";

    private final ObjectMapper objectMapper = new ObjectMapper();
    // jurisdiction -> (setName -> codes)
    private final Map<String, Map<String, Set<String>>> sets = new ConcurrentHashMap<>();

    public LookupService() {
        load();
    }

    private void load() {
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        Resource[] resources;
        try {
            resources = resolver.getResources(LOCATION_PATTERN);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to scan lookups at " + LOCATION_PATTERN, e);
        }
        for (Resource resource : resources) {
            String jurisdiction = jurisdictionOf(resource);
            String setName = stripExtension(resource.getFilename());
            try (InputStream in = resource.getInputStream()) {
                JsonNode root = objectMapper.readTree(in);
                Set<String> codes = parseCodes(root, resource.getFilename());
                sets.computeIfAbsent(jurisdiction.toLowerCase(Locale.ROOT), k -> new ConcurrentHashMap<>())
                        .put(setName.toLowerCase(Locale.ROOT), codes);
            } catch (IOException e) {
                throw new IllegalStateException("Failed to read lookup file " + resource.getFilename(), e);
            }
        }
    }

    private Set<String> parseCodes(JsonNode root, String filename) {
        if (root == null || !root.isArray()) {
            throw new IllegalStateException("Lookup file " + filename + " must be a JSON array");
        }
        Set<String> codes = new LinkedHashSet<>();
        for (JsonNode node : root) {
            if (node.isTextual()) {
                codes.add(node.asText());
            } else if (node.isObject() && node.hasNonNull("code")) {
                codes.add(node.get("code").asText());
            } else {
                throw new IllegalStateException(
                        "Lookup file " + filename + " entries must be strings or objects with a 'code' field");
            }
        }
        return codes;
    }

    /** The parent directory name of a {@code lookups/<jurisdiction>/<set>.json} resource. */
    private String jurisdictionOf(Resource resource) {
        try {
            String url = resource.getURL().toString();
            String[] parts = url.split("/lookups/", 2);
            if (parts.length == 2) {
                String tail = parts[1];
                int slash = tail.indexOf('/');
                if (slash > 0) {
                    return tail.substring(0, slash);
                }
            }
        } catch (IOException ignored) {
            // fall through
        }
        return "unknown";
    }

    private String stripExtension(String filename) {
        if (filename == null) {
            return "unknown";
        }
        int dot = filename.lastIndexOf('.');
        return dot > 0 ? filename.substring(0, dot) : filename;
    }

    /** True when the named lookup set exists for the jurisdiction (so a code check is meaningful). */
    public boolean hasSet(String jurisdiction, String setName) {
        return codes(jurisdiction, setName) != null;
    }

    /** True when {@code code} is a member of the loaded set. False if the set is loaded but lacks the code. */
    public boolean isValid(String jurisdiction, String setName, String code) {
        Set<String> codes = codes(jurisdiction, setName);
        return codes != null && code != null && codes.contains(code);
    }

    public Set<String> codes(String jurisdiction, String setName) {
        if (jurisdiction == null || setName == null) {
            return null;
        }
        Map<String, Set<String>> byJur = sets.get(jurisdiction.toLowerCase(Locale.ROOT));
        if (byJur == null) {
            return null;
        }
        return byJur.get(setName.toLowerCase(Locale.ROOT));
    }

    /** Names of the lookup sets loaded for a jurisdiction (e.g. countries, currencies). Empty if none. */
    public Set<String> setNames(String jurisdiction) {
        if (jurisdiction == null) {
            return Set.of();
        }
        Map<String, Set<String>> byJur = sets.get(jurisdiction.toLowerCase(Locale.ROOT));
        return byJur == null ? Set.of() : Set.copyOf(byJur.keySet());
    }
}
