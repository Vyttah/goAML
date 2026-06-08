package com.vyttah.goaml.repository.screening;

import com.vyttah.goaml.model.entity.screening.ScreenedSubject;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads/writes {@link ScreenedSubject} in the active tenant schema (Phase 1.5c). Lookups are by the unique
 * {@code external_ref} ({@code SCR-<companyId>-<customerUid>}) for idempotent upsert + status pull.
 */
public interface ScreenedSubjectRepository extends JpaRepository<ScreenedSubject, UUID> {

    Optional<ScreenedSubject> findByExternalRef(String externalRef);

    List<ScreenedSubject> findAllByOrderByCreatedAtDesc();
}
