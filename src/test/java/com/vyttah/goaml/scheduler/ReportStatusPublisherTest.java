package com.vyttah.goaml.scheduler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.vyttah.goaml.config.integration.OutboundIntegrationProperties;
import com.vyttah.goaml.config.scheduler.SchedulerProperties;
import com.vyttah.goaml.model.entity.federated.ReportStatusOutbox;
import com.vyttah.goaml.model.entity.federated.SourceSystem;
import com.vyttah.goaml.model.entity.federated.TenantExternalRef;
import com.vyttah.goaml.repository.federated.ReportStatusOutboxRepository;
import com.vyttah.goaml.repository.federated.TenantExternalRefRepository;
import com.vyttah.goaml.security.OutboundAssertionSigner;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Limit;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * S2 — the publisher: the wire request it makes, and what it records when the request fails.
 *
 * <p>Against a real HTTP server (WireMock, the house pattern for outbound calls here) with the repositories
 * mocked, so the assertions are about the bytes on the wire and the state left on the row — the two things S3
 * and the operator respectively depend on. The backoff is asserted as a schedule rather than a single value,
 * because a retry ladder that silently collapses to "one minute forever" looks identical in a happy-path test.
 */
class ReportStatusPublisherTest {

    private static final UUID TENANT = UUID.randomUUID();

    private static KeyPair keys;

    private WireMockServer wireMock;
    private ReportStatusOutboxRepository outboxRepository;
    private TenantExternalRefRepository tenantExternalRefRepository;

    @BeforeAll
    static void keys() throws Exception {
        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048);
        keys = gen.generateKeyPair();
    }

    @BeforeEach
    void setUp() {
        wireMock = new WireMockServer(options().dynamicPort());
        wireMock.start();
        outboxRepository = mock(ReportStatusOutboxRepository.class);
        tenantExternalRefRepository = mock(TenantExternalRefRepository.class);
        when(tenantExternalRefRepository.findByTenantIdAndSourceSystem(TENANT, SourceSystem.SCREENING))
                .thenReturn(Optional.of(new TenantExternalRef(UUID.randomUUID(), TENANT,
                        SourceSystem.SCREENING, "acme-aml")));
        when(tenantExternalRefRepository.findByTenantIdAndSourceSystem(TENANT, SourceSystem.ACCOUNTING))
                .thenReturn(Optional.of(new TenantExternalRef(UUID.randomUUID(), TENANT,
                        SourceSystem.ACCOUNTING, "3")));
    }

    @AfterEach
    void tearDown() {
        wireMock.stop();
    }

    private static String pem() {
        return "-----BEGIN PRIVATE KEY-----\n"
                + Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.US_ASCII))
                        .encodeToString(keys.getPrivate().getEncoded())
                + "\n-----END PRIVATE KEY-----\n";
    }

    private OutboundIntegrationProperties properties(boolean enabled, String baseUrl) {
        return new OutboundIntegrationProperties(enabled, pem(), "GOAML", baseUrl, "aml", 15000L, 120L, 50);
    }

    private ReportStatusPublisher publisher(boolean enabled, boolean schedulerEnabled) {
        OutboundIntegrationProperties properties = properties(enabled, wireMock.baseUrl());
        return new ReportStatusPublisher(outboxRepository, tenantExternalRefRepository,
                new OutboundAssertionSigner(properties), properties,
                new SchedulerProperties(schedulerEnabled, new SchedulerProperties.StatusPoll(false, null),
                        new SchedulerProperties.Retry(1, Duration.ofSeconds(1))),
                RestClient.builder(), bareMapper());
    }

    /**
     * Deliberately a <strong>bare</strong> mapper — no Java-time module, dates-as-timestamps still on. The
     * publisher is expected to pin both itself, and the first version of it did not: the payload could not be
     * serialised at all, so every call failed before reaching the network and the failure tests below passed
     * for entirely the wrong reason. A mapper with nothing configured is the only way to keep proving that.
     */
    private static ObjectMapper bareMapper() {
        ObjectMapper mapper = new ObjectMapper();
        assertThat(mapper.isEnabled(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)).isTrue();
        return mapper;
    }

    private ReportStatusOutbox row(UUID actor) {
        return new ReportStatusOutbox(UUID.randomUUID(), TENANT, UUID.randomUUID(), "ACC-3-REC-2026-000002",
                "REJECTED", "the buyer identification is missing", actor, OffsetDateTime.now());
    }

    private void due(ReportStatusOutbox... rows) {
        when(outboxRepository.findDue(any(OffsetDateTime.class), any(Limit.class))).thenReturn(List.of(rows));
    }

    @Test
    @DisplayName("a due row is POSTed to the AML receiver with the assertion, both company ids, and marked sent")
    void aDueRowIsDelivered() {
        UUID actor = UUID.randomUUID();
        ReportStatusOutbox row = row(actor);
        due(row);
        wireMock.stubFor(post(urlEqualTo(ReportStatusPublisher.PATH)).willReturn(aResponse().withStatus(200)));

        assertThat(publisher(true, true).publishDue()).isEqualTo(1);

        var requests = wireMock.findAll(postRequestedFor(urlEqualTo(ReportStatusPublisher.PATH)));
        assertThat(requests).hasSize(1);
        String body = requests.get(0).getBodyAsString();
        assertThat(body).contains("\"eventId\":\"" + row.getId() + "\"");
        assertThat(body).contains("\"tenantId\":\"" + TENANT + "\"");
        assertThat(body).contains("\"amlCompanyId\":\"acme-aml\"");
        assertThat(body).contains("\"erpCompanyId\":\"3\"");
        assertThat(body).contains("\"entityReference\":\"ACC-3-REC-2026-000002\"");
        assertThat(body).contains("\"status\":\"REJECTED\"");
        assertThat(body).contains("\"remark\":\"the buyer identification is missing\"");
        assertThat(body).contains("\"actorUserId\":\"" + actor + "\"");
        // ISO-8601, not an epoch number: the AML receiver and then accounting both parse this as an instant.
        assertThat(body).contains("\"occurredAt\":\"20").contains("T");

        String assertion = requests.get(0).getHeader(ReportStatusPublisher.ASSERTION_HEADER);
        assertThat(assertion).isNotBlank();
        // org must be the AML company id: it is what the AML receiver cross-checks the event body against.
        String payload = new String(Base64.getUrlDecoder().decode(assertion.split("\\.")[1]),
                StandardCharsets.UTF_8);
        assertThat(payload).contains("\"org\":\"acme-aml\"");
        assertThat(payload).contains("\"sub\":\"" + actor + "\"");

        assertThat(row.getSentAt()).isNotNull();
        assertThat(row.getLastError()).isNull();
        assertThat(row.getAttempts()).isZero();
    }

    @Test
    @DisplayName("an event with no actor is signed as 'system'")
    void aSystemEventIsSignedAsSystem() {
        due(row(null));
        wireMock.stubFor(post(urlEqualTo(ReportStatusPublisher.PATH)).willReturn(aResponse().withStatus(200)));

        publisher(true, true).publishDue();

        String assertion = wireMock.findAll(postRequestedFor(urlEqualTo(ReportStatusPublisher.PATH)))
                .get(0).getHeader(ReportStatusPublisher.ASSERTION_HEADER);
        assertThat(new String(Base64.getUrlDecoder().decode(assertion.split("\\.")[1]), StandardCharsets.UTF_8))
                .contains("\"sub\":\"" + OutboundAssertionSigner.SYSTEM_SUBJECT + "\"");
    }

    @Test
    @DisplayName("a 500 from the receiver leaves the row unsent, counted, explained, and due in a minute")
    void aFailureIsRecordedAndRetried() {
        ReportStatusOutbox row = row(UUID.randomUUID());
        due(row);
        wireMock.stubFor(post(urlEqualTo(ReportStatusPublisher.PATH)).willReturn(aResponse().withStatus(500)));

        assertThat(publisher(true, true).publishDue()).isZero();

        assertThat(row.getSentAt()).isNull();
        assertThat(row.getAttempts()).isEqualTo(1);
        // Specifically an HTTP failure. "any error at all" is what let a serialisation bug hide here once.
        assertThat(row.getLastError()).contains("500");
        assertThat(wireMock.findAll(postRequestedFor(urlEqualTo(ReportStatusPublisher.PATH)))).hasSize(1);
        assertThat(row.getNextAttemptAt()).isAfter(OffsetDateTime.now().plusSeconds(30));
        assertThat(row.getNextAttemptAt()).isBefore(OffsetDateTime.now().plusMinutes(2));
    }

    @Test
    @DisplayName("the backoff ladder grows 1m -> 5m -> 30m -> 2h -> 12h and then holds, never giving up")
    void backoffLadderGrowsAndHolds() {
        assertThat(ReportStatusPublisher.BACKOFF).containsExactly(
                Duration.ofMinutes(1), Duration.ofMinutes(5), Duration.ofMinutes(30),
                Duration.ofHours(2), Duration.ofHours(12));

        wireMock.stubFor(post(urlEqualTo(ReportStatusPublisher.PATH)).willReturn(aResponse().withStatus(503)));
        ReportStatusOutbox row = row(null);
        ReportStatusPublisher publisher = publisher(true, true);

        // Seven failures: the ladder is walked and then stays at its last rung — an event the FIU produced is
        // worth delivering late, so there is no dead-letter state to strand it in.
        Duration[] expected = {
                Duration.ofMinutes(1), Duration.ofMinutes(5), Duration.ofMinutes(30), Duration.ofHours(2),
                Duration.ofHours(12), Duration.ofHours(12), Duration.ofHours(12)
        };
        for (int attempt = 0; attempt < expected.length; attempt++) {
            due(row);
            OffsetDateTime before = OffsetDateTime.now();
            publisher.publishDue();

            assertThat(row.getAttempts()).isEqualTo(attempt + 1);
            Duration actual = Duration.between(before, row.getNextAttemptAt());
            assertThat(actual).isBetween(expected[attempt].minusSeconds(30), expected[attempt].plusSeconds(30));
        }
    }

    @Test
    @DisplayName("a tenant with no SCREENING ref cannot be signed for, so the row carries the reason")
    void aTenantWithoutAnAmlRefRecordsTheReason() {
        when(tenantExternalRefRepository.findByTenantIdAndSourceSystem(TENANT, SourceSystem.SCREENING))
                .thenReturn(Optional.empty());
        ReportStatusOutbox row = row(null);
        due(row);

        assertThat(publisher(true, true).publishDue()).isZero();

        assertThat(row.getLastError()).contains("SCREENING");
        assertThat(row.getAttempts()).isEqualTo(1);
        assertThat(wireMock.findAll(postRequestedFor(anyUrl()))).isEmpty();
    }

    @Test
    @DisplayName("one undeliverable row does not block the ones behind it")
    void oneBadRowDoesNotBlockTheCycle() {
        UUID otherTenant = UUID.randomUUID();
        ReportStatusOutbox bad = new ReportStatusOutbox(UUID.randomUUID(), otherTenant, UUID.randomUUID(),
                "ACC-9-REC-0001", "SUBMITTED", null, null, OffsetDateTime.now());
        ReportStatusOutbox good = row(null);
        when(tenantExternalRefRepository.findByTenantIdAndSourceSystem(otherTenant, SourceSystem.SCREENING))
                .thenReturn(Optional.empty());
        due(bad, good);
        wireMock.stubFor(post(urlEqualTo(ReportStatusPublisher.PATH)).willReturn(aResponse().withStatus(200)));

        assertThat(publisher(true, true).publishDue()).isEqualTo(1);

        assertThat(bad.getSentAt()).isNull();
        assertThat(good.getSentAt()).isNotNull();
    }

    @Test
    @DisplayName("the scheduled entry point is inert when outbound is off - nothing queried, nothing sent")
    void scheduledPublishIsInertWhenDisabled() {
        publisher(false, true).scheduledPublish();

        verifyNoInteractions(outboxRepository);
        assertThat(wireMock.findAll(postRequestedFor(anyUrl()))).isEmpty();
    }

    @Test
    @DisplayName("it honours the scheduler master switch, so N web replicas do not send N copies")
    void scheduledPublishHonoursTheSchedulerMasterSwitch() {
        publisher(true, false).scheduledPublish();

        verifyNoInteractions(outboxRepository);
        assertThat(wireMock.findAll(postRequestedFor(anyUrl()))).isEmpty();
    }

    @Test
    @DisplayName("the scheduled entry point never lets an exception escape - Spring would stop the schedule")
    void scheduledPublishNeverThrows() {
        when(outboxRepository.findDue(any(OffsetDateTime.class), any(Limit.class)))
                .thenThrow(new RuntimeException("database is down"));

        publisher(true, true).scheduledPublish();
    }

    @Test
    @DisplayName("an empty outbox makes no HTTP call at all")
    void anEmptyOutboxIsASilentNoOp() {
        due();

        assertThat(publisher(true, true).publishDue()).isZero();
        assertThat(wireMock.findAll(postRequestedFor(anyUrl()))).isEmpty();
    }
}
