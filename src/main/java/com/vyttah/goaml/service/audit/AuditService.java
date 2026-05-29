package com.vyttah.goaml.service.audit;

import com.vyttah.goaml.persistence.tenant.AuditLogEntity;
import com.vyttah.goaml.persistence.tenant.AuditLogRepository;
import com.vyttah.goaml.tenant.TenantContext;
import com.vyttah.goaml.tenant.TenantIdentifierResolver;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;

/**
 * Writes per-tenant {@code audit_log} rows.
 *
 * <p>Why programmatic transactions instead of {@code @Transactional}: the
 * {@link TenantContext} must be set <em>before</em> Hibernate acquires the connection
 * (which is when {@link com.vyttah.goaml.tenant.SchemaMultiTenantConnectionProvider}
 * issues the {@code SET search_path}). A method-level {@code @Transactional} would
 * have opened the connection before our callers could push the tenant onto the thread.
 *
 * <p>Platform-level actors (SUPER_ADMIN, no tenant) are skipped by default in v1 —
 * Phase 2 polish will introduce a shared platform audit table.
 */
@Service
public class AuditService {

    private final AuditLogRepository auditLogRepository;
    private final TransactionTemplate transactionTemplate;

    public AuditService(AuditLogRepository auditLogRepository,
                        PlatformTransactionManager txManager) {
        this.auditLogRepository = auditLogRepository;
        this.transactionTemplate = new TransactionTemplate(txManager);
    }

    public void recordLogin(UUID userId, String email, String tenantSchema) {
        record("USER.LOGIN", userId, email, tenantSchema, null);
    }

    /**
     * Generic audit write. Skipped if no tenant context (platform users until Phase 2 polish).
     */
    public void record(String action, UUID actorUserId, String actorEmail,
                       String tenantSchema, String summary) {
        if (tenantSchema == null || TenantIdentifierResolver.DEFAULT_TENANT.equals(tenantSchema)) {
            return;
        }
        TenantContext.set(tenantSchema);
        try {
            transactionTemplate.execute(status -> {
                AuditLogEntity row = new AuditLogEntity(UUID.randomUUID(), action, summary);
                row.setActorUserId(actorUserId);
                row.setActorEmail(actorEmail);
                return auditLogRepository.save(row);
            });
        } finally {
            TenantContext.clear();
        }
    }
}
