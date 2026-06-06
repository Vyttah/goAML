package com.vyttah.goaml.controller.lookup;

import com.vyttah.goaml.controller.lookup.LookupExceptions.LookupNotFoundException;
import com.vyttah.goaml.engine.jurisdiction.JurisdictionConfig;
import com.vyttah.goaml.engine.jurisdiction.JurisdictionRegistry;
import com.vyttah.goaml.engine.lookup.LookupService;
import com.vyttah.goaml.model.dto.lookup.LookupViews.JurisdictionView;
import com.vyttah.goaml.model.dto.lookup.LookupViews.LookupSetView;
import com.vyttah.goaml.model.dto.lookup.LookupViews.LookupSetsView;
import com.vyttah.goaml.domain.generated.ReportType;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * Read-only reference data for the UI (Phase 13): the jurisdictions the platform validates against and the
 * FIU lookup code sets (countries, currencies, indicators, …). Any authenticated user — this is reference
 * data, not tenant data — so dropdowns and client-side validation always match the backend's rules.
 */
@RestController
@RequestMapping("/api/v1/lookups")
@RequiredArgsConstructor
public class LookupController {

    private final JurisdictionRegistry jurisdictionRegistry;
    private final LookupService lookupService;

    @GetMapping("/jurisdictions")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<JurisdictionView>> jurisdictions() {
        List<JurisdictionView> views = jurisdictionRegistry.codes().stream()
                .map(jurisdictionRegistry::require)
                .map(LookupController::toView)
                .sorted(Comparator.comparing(JurisdictionView::code))
                .toList();
        return ResponseEntity.ok(views);
    }

    @GetMapping("/{jurisdiction}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<LookupSetsView> sets(@PathVariable String jurisdiction) {
        requireJurisdiction(jurisdiction);
        List<String> setNames = lookupService.setNames(jurisdiction).stream().sorted().toList();
        return ResponseEntity.ok(new LookupSetsView(jurisdiction.toLowerCase(), setNames));
    }

    @GetMapping("/{jurisdiction}/{set}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<LookupSetView> set(@PathVariable String jurisdiction, @PathVariable String set) {
        requireJurisdiction(jurisdiction);
        Set<String> codes = lookupService.codes(jurisdiction, set);
        if (codes == null) {
            throw new LookupNotFoundException(
                    "No lookup set '" + set + "' for jurisdiction '" + jurisdiction + "'");
        }
        return ResponseEntity.ok(new LookupSetView(jurisdiction.toLowerCase(), set.toLowerCase(),
                codes.stream().sorted().toList()));
    }

    private void requireJurisdiction(String jurisdiction) {
        if (jurisdictionRegistry.find(jurisdiction).isEmpty()) {
            throw new LookupNotFoundException("Unknown jurisdiction: " + jurisdiction);
        }
    }

    private static JurisdictionView toView(JurisdictionConfig c) {
        return new JurisdictionView(c.code(), c.name(), c.defaultCurrency(),
                c.allowedReportTypes().stream().map(ReportType::value).sorted().toList(),
                c.dpmsThreshold(), c.lookupSet());
    }
}
