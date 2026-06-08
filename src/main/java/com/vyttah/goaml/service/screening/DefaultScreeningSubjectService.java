package com.vyttah.goaml.service.screening;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vyttah.goaml.model.dto.integration.ScreeningPartyPayload;
import com.vyttah.goaml.model.dto.integration.ScreeningSeedRequest;
import com.vyttah.goaml.model.dto.integration.ScreeningSubjectResponse;
import com.vyttah.goaml.model.dto.report.DpmsrCreateRequest;
import com.vyttah.goaml.model.entity.screening.ScreenedSubject;
import com.vyttah.goaml.repository.screening.ScreenedSubjectRepository;
import com.vyttah.goaml.service.integration.IntegrationExceptions;
import com.vyttah.goaml.service.integration.ScreeningPartyMapper;
import com.vyttah.goaml.service.report.ReportResult;
import com.vyttah.goaml.service.report.ReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * Default {@link ScreeningSubjectService} (Phase 1.5c.3). Reads {@link ScreenedSubject} from the caller's
 * bound tenant and seeds a DPMSR draft by combining the subject's mapped parties with the caller-supplied
 * goods/report fields, delegating to the existing {@link ReportService#create}.
 */
@RequiredArgsConstructor
@Service
public class DefaultScreeningSubjectService implements ScreeningSubjectService {

    private final ScreenedSubjectRepository screenedSubjects;
    private final ReportService reportService;
    private final ObjectMapper objectMapper;

    @Override
    public List<ScreeningSubjectResponse> list() {
        return screenedSubjects.findAllByOrderByCreatedAtDesc().stream()
                .map(s -> response(s.getExternalRef(), deserialize(s.getPayloadJson())))
                .toList();
    }

    @Override
    public ScreeningSubjectResponse get(String subjectRef) {
        ScreenedSubject subject = find(subjectRef);
        return response(subject.getExternalRef(), deserialize(subject.getPayloadJson()));
    }

    @Override
    public ReportResult seedReport(String subjectRef, ScreeningSeedRequest request, UUID tenantId,
                                   UUID actorUserId) {
        ScreenedSubject subject = find(subjectRef);
        List<DpmsrCreateRequest.Party> parties =
                ScreeningPartyMapper.toParties(deserialize(subject.getPayloadJson()));

        DpmsrCreateRequest req = new DpmsrCreateRequest(
                null,
                request.entityReference(),
                request.submissionDate(),
                null,
                request.reason(),
                request.action(),
                request.indicators(),
                request.reportingPerson(),
                request.location(),
                parties,
                request.goods());
        return reportService.create(req, tenantId, actorUserId);
    }

    private ScreenedSubject find(String subjectRef) {
        return screenedSubjects.findByExternalRef(subjectRef)
                .orElseThrow(() -> new IntegrationExceptions.ScreenedSubjectNotFoundException(
                        "No screened subject " + subjectRef));
    }

    private ScreeningSubjectResponse response(String ref, ScreeningPartyPayload payload) {
        return new ScreeningSubjectResponse(ref, payload.subjectType().name(),
                ScreeningPartyMapper.displayName(payload),
                payload.sanctions() != null && payload.sanctions().riskFlag(),
                ScreeningPartyMapper.toParties(payload),
                ScreeningPartyMapper.sanctionsContext(payload));
    }

    private ScreeningPartyPayload deserialize(String json) {
        try {
            return objectMapper.readValue(json, ScreeningPartyPayload.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not read stored screening payload: " + e.getMessage(), e);
        }
    }
}
