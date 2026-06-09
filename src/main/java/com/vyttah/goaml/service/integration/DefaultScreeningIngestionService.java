package com.vyttah.goaml.service.integration;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vyttah.goaml.config.tenant.TenantContext;
import com.vyttah.goaml.model.dto.integration.ScreeningPartyPayload;
import com.vyttah.goaml.model.dto.integration.ScreeningSubjectResponse;
import com.vyttah.goaml.model.dto.report.DpmsrCreateRequest;
import com.vyttah.goaml.model.entity.federated.SourceSystem;
import com.vyttah.goaml.model.entity.screening.ScreenedSubject;
import com.vyttah.goaml.model.entity.tenant.Tenant;
import com.vyttah.goaml.repository.federated.TenantExternalRefRepository;
import com.vyttah.goaml.repository.screening.ScreenedSubjectRepository;
import com.vyttah.goaml.repository.tenant.TenantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * Default {@link ScreeningIngestionService} (Phase 1.5c). Resolves the screening company → goAML tenant, binds
 * the tenant {@link TenantContext} (the push has no user JWT, so it binds the schema itself — like the
 * accounting push), maps the payload to a goAML party set, and upserts a reusable {@code screened_subject}.
 * Idempotent on {@code SCR-<companyId>-<customerUid>}.
 */
@RequiredArgsConstructor
@Service
public class DefaultScreeningIngestionService implements ScreeningIngestionService {

    private final TenantExternalRefRepository tenantExternalRefs;
    private final TenantRepository tenants;
    private final ScreenedSubjectRepository screenedSubjects;
    private final ObjectMapper objectMapper;

    @Override
    public ScreeningSubjectResponse ingest(ScreeningPartyPayload payload) {
        ResolvedTenant tenant = resolveTenant(payload.companyId());
        String ref = reference(payload.companyId(), payload.customerUid());
        String payloadJson = serialize(payload);
        String displayName = ScreeningPartyMapper.displayName(payload);
        boolean riskFlag = payload.sanctions() != null && payload.sanctions().riskFlag();

        String previous = TenantContext.get();
        TenantContext.set(tenant.schema());
        try {
            ScreenedSubject subject = screenedSubjects.findByExternalRef(ref)
                    .map(existing -> {
                        existing.setSubjectType(payload.subjectType().name());
                        existing.setDisplayName(displayName);
                        existing.setRiskFlag(riskFlag);
                        existing.setPayloadJson(payloadJson);
                        return existing;
                    })
                    .orElseGet(() -> new ScreenedSubject(UUID.randomUUID(), ref,
                            payload.subjectType().name(), displayName, riskFlag, payloadJson));
            screenedSubjects.save(subject);
            return response(ref, payload);
        } finally {
            restore(previous);
        }
    }

    @Override
    public ScreeningSubjectResponse get(String companyId, String customerUid) {
        ResolvedTenant tenant = resolveTenant(companyId);
        String ref = reference(companyId, customerUid);
        String previous = TenantContext.get();
        TenantContext.set(tenant.schema());
        try {
            ScreenedSubject subject = screenedSubjects.findByExternalRef(ref)
                    .orElseThrow(() -> new IntegrationExceptions.ScreenedSubjectNotFoundException(
                            "No screened subject for customer " + customerUid));
            return response(ref, deserialize(subject.getPayloadJson()));
        } finally {
            restore(previous);
        }
    }

    @Override
    public List<ScreeningSubjectResponse> list(String companyId) {
        ResolvedTenant tenant = resolveTenant(companyId);
        String prefix = referencePrefix(companyId);
        String previous = TenantContext.get();
        TenantContext.set(tenant.schema());
        try {
            return screenedSubjects.findAllByOrderByCreatedAtDesc().stream()
                    .filter(s -> s.getExternalRef() != null && s.getExternalRef().startsWith(prefix))
                    .map(s -> response(s.getExternalRef(), deserialize(s.getPayloadJson())))
                    .toList();
        } finally {
            restore(previous);
        }
    }

    private ScreeningSubjectResponse response(String ref, ScreeningPartyPayload payload) {
        List<DpmsrCreateRequest.Party> parties = ScreeningPartyMapper.toParties(payload);
        return new ScreeningSubjectResponse(ref, payload.subjectType().name(),
                ScreeningPartyMapper.displayName(payload),
                payload.sanctions() != null && payload.sanctions().riskFlag(),
                parties, ScreeningPartyMapper.sanctionsContext(payload));
    }

    private ResolvedTenant resolveTenant(String companyId) {
        UUID tenantId = tenantExternalRefs
                .findBySourceSystemAndExternalOrgRef(SourceSystem.SCREENING, companyId)
                .map(ref -> ref.getTenantId())
                .orElseThrow(() -> new IntegrationExceptions.UnmappedOrgException(
                        "No goAML tenant mapped to screening company " + companyId));
        Tenant tenant = tenants.findById(tenantId)
                .orElseThrow(() -> new IntegrationExceptions.UnmappedOrgException(
                        "Mapped tenant no longer exists for screening company " + companyId));
        return new ResolvedTenant(tenant.getId(), tenant.getSchemaName());
    }

    private String serialize(ScreeningPartyPayload payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Could not serialize screening payload: " + e.getMessage(), e);
        }
    }

    private ScreeningPartyPayload deserialize(String json) {
        try {
            return objectMapper.readValue(json, ScreeningPartyPayload.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not read stored screening payload: " + e.getMessage(), e);
        }
    }

    private static String reference(String companyId, String customerUid) {
        return referencePrefix(companyId) + customerUid;
    }

    private static String referencePrefix(String companyId) {
        return "SCR-" + companyId + "-";
    }

    private static void restore(String previous) {
        if (previous != null) {
            TenantContext.set(previous);
        } else {
            TenantContext.clear();
        }
    }

    private record ResolvedTenant(UUID tenantId, String schema) {}
}
