package com.vyttah.goaml.config.integration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Outbound suite-integration settings, bound from {@code goaml.integration.outbound.*} (goAML ↔ ERP wiring,
 * Phase 2 / S2, decision D22).
 *
 * <p>This is goAML's first <strong>outbound</strong> integration with a sibling Vyttah system: until now goAML
 * only verified assertions other systems presented, and the only thing it ever called was the FIU. Here it
 * signs its own assertion ({@code iss=GOAML}) and pushes report-status events to the AML platform, which
 * forwards them to the ERP.
 *
 * @param enabled              master switch, <strong>off by default</strong>. While it is off nothing is
 *                             recorded and nothing is published — see
 *                             {@code ReportStatusOutboxService} for why no backlog is accumulated.
 * @param privateKeyPem        the PKCS#8 RSA private key this instance signs with. A credential: keep it in env
 *                             / secrets, never in VCS. Its public half is registered on the AML side as a
 *                             {@code GOAML} row in {@code trusted_service} (owner-generated, as T1).
 * @param issuer               the {@code iss} the AML side knows goAML as. Do not change it without changing
 *                             the {@code trusted_service} row that resolves the verification key.
 * @param amlBaseUrl           root URL of the AML customer-service, e.g. {@code http://localhost:8081}.
 * @param amlAudience          the {@code aud} the AML receiver requires.
 * @param intervalMs           delay between publish cycles.
 * @param assertionLifetimeSeconds assertion lifetime. The receiver caps it at 300 s, so this must stay under it.
 * @param batchSize            most rows one cycle will attempt, so a large backlog is drained over several
 *                             cycles instead of holding the scheduler thread for one long burst.
 */
@ConfigurationProperties("goaml.integration.outbound")
public record OutboundIntegrationProperties(@DefaultValue("false") boolean enabled,
                                            @DefaultValue("") String privateKeyPem,
                                            @DefaultValue("GOAML") String issuer,
                                            @DefaultValue("") String amlBaseUrl,
                                            @DefaultValue("aml") String amlAudience,
                                            @DefaultValue("15000") long intervalMs,
                                            @DefaultValue("120") long assertionLifetimeSeconds,
                                            @DefaultValue("50") int batchSize) {

    /**
     * True when this instance is actually able to publish: the switch is on, a signing key is present and a
     * destination is configured. Anything less is treated as "not configured" rather than as an error, because
     * a standalone goAML deployment has no AML platform to talk to and must keep working.
     */
    public boolean usable() {
        return enabled && !privateKeyPem.isBlank() && !amlBaseUrl.isBlank();
    }
}
