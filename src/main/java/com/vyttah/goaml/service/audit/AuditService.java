package com.vyttah.goaml.service.audit;

import java.util.UUID;

/**
 * Writes per-tenant {@code audit_log} rows. Implemented by {@link DefaultAuditService}.
 *
 * <p>Platform-level actors (SUPER_ADMIN, no tenant) are skipped by default in v1 — Phase 2 polish
 * will introduce a shared platform audit table.
 */
public interface AuditService {

    void recordLogin(UUID userId, String email, String tenantSchema);

    /** Generic audit write. Skipped if no tenant context (platform users until Phase 2 polish). */
    void record(String action, UUID actorUserId, String actorEmail, String tenantSchema, String summary);
}
