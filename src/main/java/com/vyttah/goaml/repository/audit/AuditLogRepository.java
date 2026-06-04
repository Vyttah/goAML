package com.vyttah.goaml.repository.audit;

import com.vyttah.goaml.model.entity.audit.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

/**
 * JPA repository for the per-tenant audit log. Every call must be made with a tenant bound
 * to {@link com.vyttah.goaml.config.tenant.TenantContext}, otherwise Hibernate will route the
 * query to {@code public} and fail.
 */
public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {
}
