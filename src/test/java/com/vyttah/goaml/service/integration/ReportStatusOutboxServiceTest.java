package com.vyttah.goaml.service.integration;

import com.vyttah.goaml.config.integration.OutboundIntegrationProperties;
import com.vyttah.goaml.model.entity.federated.ReportStatusOutbox;
import com.vyttah.goaml.model.entity.federated.SourceSystem;
import com.vyttah.goaml.model.entity.federated.TenantExternalRef;
import com.vyttah.goaml.model.entity.report.Report;
import com.vyttah.goaml.repository.federated.ReportStatusOutboxRepository;
import com.vyttah.goaml.repository.federated.TenantExternalRefRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * S2 — what gets recorded, and what deliberately does not.
 *
 * <p>Three of these tests are about <em>not</em> writing a row, which is most of this class's job: an
 * integration that is switched off has to be inert rather than quietly stockpiling events, a tenant with no
 * ERP has nobody waiting for the answer, and a failure in the outbox must never become the reason a
 * compliance action fails.
 */
class ReportStatusOutboxServiceTest {

    private static final UUID TENANT = UUID.randomUUID();

    private ReportStatusOutboxRepository outboxRepository;
    private TenantExternalRefRepository tenantExternalRefRepository;

    @BeforeEach
    void setUp() {
        outboxRepository = mock(ReportStatusOutboxRepository.class);
        tenantExternalRefRepository = mock(TenantExternalRefRepository.class);
    }

    private ReportStatusOutboxService service(boolean enabled) {
        OutboundIntegrationProperties properties = new OutboundIntegrationProperties(enabled,
                enabled ? "a-key" : "", "GOAML", enabled ? "http://localhost:8081" : "", "aml", 15000L, 120L, 50);
        return new ReportStatusOutboxService(outboxRepository, tenantExternalRefRepository, properties);
    }

    private void withAccountingRef() {
        when(tenantExternalRefRepository.findByTenantIdAndSourceSystem(TENANT, SourceSystem.ACCOUNTING))
                .thenReturn(Optional.of(new TenantExternalRef(UUID.randomUUID(), TENANT,
                        SourceSystem.ACCOUNTING, "3")));
    }

    private static Report report() {
        return new Report(UUID.randomUUID(), "ACC-3-REC-2026-000002", "DPMSR", 3177, "VALID", "{}",
                UUID.randomUUID());
    }

    @Test
    @DisplayName("a transition on an ERP-filed tenant is queued with the transition, the remark and the actor")
    void transitionIsQueued() {
        withAccountingRef();
        Report report = report();
        UUID actor = UUID.randomUUID();

        assertThat(service(true).record(report, TENANT, "REJECTED", "the buyer ID is missing", actor)).isTrue();

        ArgumentCaptor<ReportStatusOutbox> saved = ArgumentCaptor.forClass(ReportStatusOutbox.class);
        verify(outboxRepository).save(saved.capture());
        ReportStatusOutbox row = saved.getValue();
        assertThat(row.getTenantId()).isEqualTo(TENANT);
        assertThat(row.getReportId()).isEqualTo(report.getId());
        assertThat(row.getEntityReference()).isEqualTo("ACC-3-REC-2026-000002");
        assertThat(row.getStatus()).isEqualTo("REJECTED");
        assertThat(row.getRemark()).isEqualTo("the buyer ID is missing");
        assertThat(row.getActorUserId()).isEqualTo(actor);
        assertThat(row.getOccurredAt()).isNotNull();
        // A fresh row is due immediately and carries no failure history.
        assertThat(row.getAttempts()).isZero();
        assertThat(row.getNextAttemptAt()).isNull();
        assertThat(row.getSentAt()).isNull();
    }

    @Test
    @DisplayName("a background poll has no actor, which is recorded as null rather than invented")
    void aPollHasNoActor() {
        withAccountingRef();

        assertThat(service(true).record(report(), TENANT, "ACCEPTED", null, null)).isTrue();

        ArgumentCaptor<ReportStatusOutbox> saved = ArgumentCaptor.forClass(ReportStatusOutbox.class);
        verify(outboxRepository).save(saved.capture());
        assertThat(saved.getValue().getActorUserId()).isNull();
        assertThat(saved.getValue().getRemark()).isNull();
    }

    @Test
    @DisplayName("switched off means inert: nothing is written and the tenant is not even looked up")
    void disabledRecordsNothing() {
        assertThat(service(false).record(report(), TENANT, "SUBMITTED", null, UUID.randomUUID())).isFalse();

        verifyNoInteractions(outboxRepository);
        // Not merely "writes nothing": an off switch should cost nothing either.
        verifyNoInteractions(tenantExternalRefRepository);
    }

    @Test
    @DisplayName("a tenant with no ACCOUNTING ref has no ERP waiting, so nothing is queued")
    void tenantWithoutAnErpRecordsNothing() {
        when(tenantExternalRefRepository.findByTenantIdAndSourceSystem(TENANT, SourceSystem.ACCOUNTING))
                .thenReturn(Optional.empty());

        assertThat(service(true).record(report(), TENANT, "SUBMITTED", null, UUID.randomUUID())).isFalse();

        verify(outboxRepository, never()).save(any());
    }

    @Test
    @DisplayName("an outbox failure is never the reason a submission fails")
    void aFailureIsSwallowed() {
        withAccountingRef();
        when(outboxRepository.save(any())).thenThrow(new RuntimeException("outbox table is gone"));

        assertThatCode(() -> assertThat(service(true)
                .record(report(), TENANT, "SUBMITTED", null, UUID.randomUUID())).isFalse())
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("a missing report or tenant is a no-op, not a null pointer")
    void nullsAreNoOps() {
        ReportStatusOutboxService service = service(true);

        assertThat(service.record(null, TENANT, "SUBMITTED", null, null)).isFalse();
        assertThat(service.record(report(), null, "SUBMITTED", null, null)).isFalse();
        verifyNoInteractions(outboxRepository);
    }
}
