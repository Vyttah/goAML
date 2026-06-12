package com.vyttah.goaml.repository.federated;

import com.vyttah.goaml.model.entity.federated.ConsumedAssertion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;

/**
 * Replay store for verified service assertions (shared {@code public.consumed_assertion}).
 * {@code ServiceCredentialValidator} records each assertion's {@code jti} on first use and refuses a second
 * use of the same {@code jti} before its expiry.
 */
public interface ConsumedAssertionRepository extends JpaRepository<ConsumedAssertion, String> {

    /**
     * Opportunistic cleanup — drop rows whose assertion has already expired (a replay of an expired
     * assertion is rejected by the exp check anyway, so the row is redundant). Returns the number deleted.
     */
    @Modifying
    @Query("delete from ConsumedAssertion c where c.expiresAt < :now")
    int deleteExpired(@Param("now") OffsetDateTime now);
}
