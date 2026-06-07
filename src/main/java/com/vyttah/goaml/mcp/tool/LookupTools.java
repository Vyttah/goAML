package com.vyttah.goaml.mcp.tool;

import com.vyttah.goaml.domain.generated.ReportType;
import com.vyttah.goaml.engine.jurisdiction.JurisdictionConfig;
import com.vyttah.goaml.engine.jurisdiction.JurisdictionRegistry;
import com.vyttah.goaml.engine.lookup.LookupService;
import com.vyttah.goaml.engine.metadata.ReportTypeMetadata;
import com.vyttah.goaml.mcp.McpIdentity;
import com.vyttah.goaml.model.dto.lookup.LookupViews.JurisdictionView;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * Read-only MCP tools over the engine's reference data — jurisdictions, lookup sets/codes, and report-type
 * shape metadata. These let an agent discover what a tenant's FIU accepts and how to build a correct report
 * <em>before</em> validating against the real validator. All require authentication (any role); they delegate
 * to the same {@link JurisdictionRegistry} / {@link LookupService} the REST lookup API uses.
 */
@Component
public class LookupTools {

    private final JurisdictionRegistry jurisdictionRegistry;
    private final LookupService lookupService;

    public LookupTools(JurisdictionRegistry jurisdictionRegistry, LookupService lookupService) {
        this.jurisdictionRegistry = jurisdictionRegistry;
        this.lookupService = lookupService;
    }

    /** Shape + conditional-field requirements for a report type, and whether a jurisdiction accepts it. */
    public record ReportTypeDescription(
            String reportCode,
            String shape,
            boolean fiuRefRequired,
            boolean locationReasonActionRequired,
            String jurisdiction,
            boolean allowedInJurisdiction,
            BigDecimal dpmsThreshold) {}

    @Tool(name = "goaml_list_jurisdictions",
            description = "List the jurisdictions (FIUs) the platform validates against, each with its name, "
                    + "local currency, accepted report codes, DPMS cash threshold, and lookup set. Read-only.")
    public List<JurisdictionView> listJurisdictions() {
        McpIdentity.require();
        return jurisdictionRegistry.codes().stream()
                .sorted()
                .map(jurisdictionRegistry::require)
                .map(LookupTools::toView)
                .toList();
    }

    @Tool(name = "goaml_list_lookup_sets",
            description = "List the lookup-set names available for a jurisdiction (e.g. countries, "
                    + "currencies, transmode, fund types). Read-only.")
    public List<String> listLookupSets(
            @ToolParam(description = "Jurisdiction code, e.g. 'ae' for the UAE.") String jurisdiction) {
        McpIdentity.require();
        return lookupService.setNames(jurisdiction).stream().sorted().toList();
    }

    @Tool(name = "goaml_list_lookups",
            description = "List the valid codes in one lookup set for a jurisdiction (e.g. all currency codes). "
                    + "Use these exact codes when building a report so it passes validation. Read-only.")
    public List<String> listLookups(
            @ToolParam(description = "Jurisdiction code, e.g. 'ae'.") String jurisdiction,
            @ToolParam(description = "Lookup set name, e.g. 'currencies' (see goaml_list_lookup_sets).")
            String set) {
        McpIdentity.require();
        Set<String> codes = lookupService.codes(jurisdiction, set);
        return codes == null ? List.of() : codes.stream().sorted().toList();
    }

    @Tool(name = "goaml_describe_report_type",
            description = "Describe a goAML report type: its shape (TRANSACTION vs ACTIVITY), whether it "
                    + "requires an FIU reference or location/reason/action, whether a given jurisdiction accepts "
                    + "it, and (for DPMSR) the cash threshold. Call this before building a report. Read-only.")
    public ReportTypeDescription describeReportType(
            @ToolParam(description = "Report code, e.g. 'DPMSR', 'STR', 'SAR'.") String reportCode,
            @ToolParam(required = false, description = "Jurisdiction code; defaults to 'ae'.") String jurisdiction) {
        McpIdentity.require();
        ReportType code = parseReportCode(reportCode);
        ReportTypeMetadata.Descriptor descriptor = ReportTypeMetadata.describe(code);

        String jur = (jurisdiction == null || jurisdiction.isBlank()) ? "ae" : jurisdiction.toLowerCase();
        JurisdictionConfig config = jurisdictionRegistry.find(jur).orElse(null);
        boolean allowed = config != null && config.allows(code);
        BigDecimal dpmsThreshold = (code == ReportType.DPMSR && config != null) ? config.dpmsThreshold() : null;

        return new ReportTypeDescription(descriptor.code(), descriptor.shape(), descriptor.fiuRefRequired(),
                descriptor.locationReasonActionRequired(), jur, allowed, dpmsThreshold);
    }

    private static ReportType parseReportCode(String reportCode) {
        try {
            return ReportType.fromValue(reportCode);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Unknown report_code '" + reportCode + "'. Valid codes: "
                    + Arrays.stream(ReportType.values()).map(ReportType::value).sorted().toList());
        }
    }

    private static JurisdictionView toView(JurisdictionConfig config) {
        return new JurisdictionView(
                config.code(),
                config.name(),
                config.defaultCurrency(),
                config.allowedReportTypes().stream().map(ReportType::value).sorted().toList(),
                config.dpmsThreshold(),
                config.lookupSet());
    }
}
