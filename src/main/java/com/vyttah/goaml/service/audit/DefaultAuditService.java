package com.vyttah.goaml.service.audit;

import com.vyttah.goaml.config.tenant.TenantContext;
import com.vyttah.goaml.config.tenant.TenantIdentifierResolver;
import com.vyttah.goaml.model.entity.audit.AuditLog;
import com.vyttah.goaml.repository.audit.AuditLogRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;

/**
 * Default {@link AuditService} — writes per-tenant {@code audit_log} rows.
 *
 * <p>Why programmatic transactions instead of {@code @Transactional}: the
 * {@link TenantContext} must be set <em>before</em> Hibernate acquires the connection
 * (which is when {@link com.vyttah.goaml.config.tenant.SchemaMultiTenantConnectionProvider}
 * issues the {@code SET search_path}). A method-level {@code @Transactional} would
 * have opened the connection before our callers could push the tenant onto the thread.
 */
@Service
public class DefaultAuditService implements AuditService {

    private final AuditLogRepository auditLogRepository;
    private final TransactionTemplate transactionTemplate;

    public DefaultAuditService(AuditLogRepository auditLogRepository,
                               PlatformTransactionManager txManager) {
        this.auditLogRepository = auditLogRepository;
        this.transactionTemplate = new TransactionTemplate(txManager);
    }

    @Override
    public void recordLogin(UUID userId, String email, String tenantSchema) {
        record("USER.LOGIN", userId, email, tenantSchema, null);
    }

    @Override
    public void record(String action, UUID actorUserId, String actorEmail,
                       String tenantSchema, String summary) {
        if (tenantSchema == null || TenantIdentifierResolver.DEFAULT_TENANT.equals(tenantSchema)) {
            return;
        }
        TenantContext.set(tenantSchema);
        try {
            transactionTemplate.execute(status -> {
                AuditLog row = new AuditLog(UUID.randomUUID(), action, summary);
                row.setActorUserId(actorUserId);
                row.setActorEmail(actorEmail);
                return auditLogRepository.save(row);
            });
        } finally {
            TenantContext.clear();
        }
    }
}
