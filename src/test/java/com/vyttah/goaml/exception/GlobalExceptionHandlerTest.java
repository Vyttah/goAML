package com.vyttah.goaml.exception;

import com.vyttah.goaml.integration.aws.S3AccessException;
import com.vyttah.goaml.integration.aws.SecretsAccessException;
import com.vyttah.goaml.service.submission.SubmissionExceptions;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link GlobalExceptionHandler}. Focus on the AWS-integration mapping added during the
 * post-verification hardening: a missing/misconfigured FIU secret (or an S3 failure) must surface as a clean
 * {@code 502} with a body, not a bare {@code 500}.
 */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void secretsAccessFailureMapsToBadGatewayWithBody() {
        ResponseEntity<Map<String, Object>> resp =
                handler.handleAwsIntegration(new SecretsAccessException("Secret not found: goaml/tenants/x/fiu"));

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
        assertThat(resp.getBody()).containsEntry("status", 502);
        assertThat(resp.getBody().get("message").toString()).contains("Secret not found");
    }

    @Test
    void s3AccessFailureMapsToBadGateway() {
        ResponseEntity<Map<String, Object>> resp =
                handler.handleAwsIntegration(new S3AccessException("boom"));

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
        assertThat(resp.getBody()).containsEntry("error", "Bad Gateway");
    }

    @Test
    void transportFailureStillMapsToBadGateway() {
        ResponseEntity<Map<String, Object>> resp = handler.handleTransport(
                new SubmissionExceptions.SubmissionTransportException("x", new RuntimeException()));

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
    }
}
