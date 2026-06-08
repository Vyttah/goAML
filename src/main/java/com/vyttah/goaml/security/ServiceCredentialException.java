package com.vyttah.goaml.security;

/**
 * The inbound service assertion (Phase 1.5 token-exchange / integration push) failed verification — unknown
 * or disabled source, bad signature, expired/over-long, or wrong audience. Mapped to {@code 401} (the
 * calling <em>service</em> failed to authenticate). Carries no detail that distinguishes failure modes to a
 * remote caller beyond a generic message.
 */
public class ServiceCredentialException extends RuntimeException {
    public ServiceCredentialException(String message) {
        super(message);
    }
}
