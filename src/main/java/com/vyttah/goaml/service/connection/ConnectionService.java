package com.vyttah.goaml.service.connection;

import com.vyttah.goaml.model.dto.connection.ConnectionViews.ConnectionView;

import java.util.UUID;

/**
 * Assembles the read-only "my goAML connection" summary (linked tenant + active reporting person + whether
 * FIU B2B config is set) for an authenticated user. Implemented by {@link DefaultConnectionService}.
 */
public interface ConnectionService {

    /**
     * @throws com.vyttah.goaml.service.admin.AdminExceptions.TenantNotFoundException if the caller's tenant
     *         no longer exists (should not happen for a valid session)
     */
    ConnectionView getConnection(UUID tenantId);
}
