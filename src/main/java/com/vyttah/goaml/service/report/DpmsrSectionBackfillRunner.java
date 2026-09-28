package com.vyttah.goaml.service.report;

import com.vyttah.goaml.config.tenant.TenantContext;
import com.vyttah.goaml.model.entity.report.Report;
import com.vyttah.goaml.model.entity.tenant.Tenant;
import com.vyttah.goaml.repository.report.ReportRepository;
import com.vyttah.goaml.repository.tenant.TenantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * One-time historical backfill (migrate-json-storage-to-sql §7 point 5, step 2): populates the DPMSR
 * section tables for every report that predates the dual-write going live, across every ACTIVE tenant.
 * New reports created after that point already dual-write via {@link DefaultReportService}; this covers
 * the ones that existed before.
 *
 * <p><strong>Off by default</strong> — gated by {@code goaml.dpmsr-backfill.enabled=true} so it never
 * runs during normal app startup, tests, or an accidental production boot. Run it explicitly once (e.g.
 * {@code GOAML_DPMSR_BACKFILL_ENABLED=true ./gradlew bootRun}), then leave it disabled — safe to leave in
 * the codebase afterward since it's idempotent ({@link DpmsrSectionBackfillService#alreadyBackfilled}
 * skips anything already backfilled) and every other environment (staging/prod) will eventually need the
 * same one-time run when this schema change reaches them.
 *
 * <p>Per-report failures are logged and skipped, not fatal — one bad historical row must not abort the
 * whole batch. {@link TenantContext} is always cleared per tenant so a failure can't leak into the next.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "goaml.dpmsr-backfill", name = "enabled", havingValue = "true")
public class DpmsrSectionBackfillRunner implements ApplicationRunner {

    private static final String ACTIVE = "ACTIVE";

    private final TenantRepository tenantRepository;
    private final ReportRepository reportRepository;
    private final DpmsrSectionBackfillService backfillService;

    @Override
    public void run(ApplicationArguments args) {
        List<Tenant> tenants = tenantRepository.findByStatus(ACTIVE);
        log.info("DPMSR section backfill: starting across {} ACTIVE tenant(s)", tenants.size());

        int scanned = 0;
        int backfilled = 0;
        int alreadyDone = 0;
        int failed = 0;

        for (Tenant tenant : tenants) {
            try {
                TenantContext.set(tenant.getSchemaName());
                List<Report> reports = reportRepository.findAll();
                for (Report report : reports) {
                    scanned++;
                    try {
                        if (backfillService.alreadyBackfilled(report.getId())) {
                            alreadyDone++;
                            continue;
                        }
                        backfillService.backfillOne(report);
                        backfilled++;
                    } catch (RuntimeException e) {
                        failed++;
                        log.error("DPMSR section backfill failed for report {} (tenant {}): {}",
                                report.getId(), tenant.getId(), e.getMessage(), e);
                    }
                }
            } finally {
                TenantContext.clear();
            }
        }

        log.info("DPMSR section backfill complete: {} scanned, {} backfilled, {} already done, {} failed",
                scanned, backfilled, alreadyDone, failed);
    }
}
