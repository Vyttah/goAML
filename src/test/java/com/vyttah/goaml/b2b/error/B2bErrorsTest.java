package com.vyttah.goaml.b2b.error;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class B2bErrorsTest {

    @Test
    void authExceptionCarriesMessageAndCause() {
        Throwable cause = new IllegalStateException("x");
        B2bAuthException e = new B2bAuthException("auth", cause);
        assertThat(e).hasMessage("auth").hasCause(cause);
        assertThat(new B2bAuthException("just-msg").getCause()).isNull();
    }

    @Test
    void transportExceptionCarriesMessageAndCause() {
        Throwable cause = new RuntimeException("y");
        assertThat(new B2bTransportException("t", cause)).hasMessage("t").hasCause(cause);
        assertThat(new B2bTransportException("t2").getCause()).isNull();
    }

    @Test
    void validationExceptionExposesResponseBody() {
        B2bValidationException e = new B2bValidationException("rejected", "<error>bad</error>");
        assertThat(e).hasMessage("rejected");
        assertThat(e.responseBody()).isEqualTo("<error>bad</error>");
    }
}
