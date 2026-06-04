package com.vyttah.goaml.model.dto.me;

import java.util.List;
import java.util.UUID;

/** The currently-authenticated user, as returned by {@code GET /api/v1/me}. */
public record MeResponse(UUID userId, UUID tenantId, String tenantSchema,
                         String email, List<String> roles) {}
