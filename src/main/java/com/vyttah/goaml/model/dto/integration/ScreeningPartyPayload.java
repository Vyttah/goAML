package com.vyttah.goaml.model.dto.integration;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * AML screening → goAML party push (Phase 1.5c). A self-contained, already-<b>resolved</b> view of a screened
 * customer: the screening software resolves its master-data foreign keys (nationality, id-type, country, …) to
 * ISO / goAML codes <i>before</i> pushing, so goAML never calls back into a sibling microservice (the locked
 * suite-integration rule, mirroring the accounting push).
 *
 * <p>Carries the customer (natural or legal), its related parties (directors / shareholders / UBOs), and the
 * sanctions-screening verdict. goAML maps this onto a reusable party set ({@code ScreeningPartyMapper}) that can
 * seed a report's parties — see {@code docs/14-suite-integration.md}.
 *
 * @param companyId    the screening org id → resolves the goAML tenant via {@code tenant_external_ref}
 * @param customerUid  the screening customer's stable id → the idempotency key {@code SCR-<companyId>-<uid>}
 * @param subjectType  whether the customer is a natural person or a legal entity
 * @param natural      the customer when {@link SubjectType#NATURAL} (else null)
 * @param legal        the customer when {@link SubjectType#LEGAL} (else null)
 * @param directors    the legal customer's directors (mapped onto the goAML entity's directors)
 * @param shareholders the customer's shareholders (mapped onto additional report parties)
 * @param ubos         the customer's ultimate beneficial owners (mapped onto additional report parties)
 * @param sanctions    the sanctions-screening verdict (risk flag + hits), recorded as party/report context
 */
public record ScreeningPartyPayload(
        @NotNull Integer companyId,
        @NotBlank String customerUid,
        @NotNull SubjectType subjectType,
        NaturalCustomer natural,
        LegalCustomer legal,
        List<RelatedParty> directors,
        List<RelatedParty> shareholders,
        List<RelatedParty> ubos,
        Sanctions sanctions) {

    public enum SubjectType { NATURAL, LEGAL }

    public record NaturalCustomer(
            String gender,
            String firstName,
            String lastName,
            String alias,
            LocalDate dob,
            String nationality,
            String countryOfBirth,
            String residence,
            String emiratesId,
            String occupation,
            String email,
            Phone phone,
            Address address,
            List<Identification> identifications,
            boolean pep) {}

    public record LegalCustomer(
            String legalName,
            String commercialName,
            String incorporationNumber,
            String incorporationState,
            String incorporationCountry,
            LocalDate dateOfIncorporation,
            String licenseNumber,
            String trn,
            Phone phone,
            Address address) {}

    /** A director / shareholder / UBO — natural unless {@code partyType="LEGAL"}. */
    public record RelatedParty(
            String partyType,
            String fullName,
            String firstName,
            String lastName,
            LocalDate dob,
            String nationality,
            String residence,
            boolean pep,
            BigDecimal shareholdingPercent,
            String idType,
            String idNumber,
            String idCountry,
            String legalName,
            String incorporationNumber,
            String incorporationCountry,
            Phone phone) {}

    public record Identification(
            String type,
            String number,
            LocalDate issueDate,
            LocalDate expiryDate,
            String issueCountry) {}

    public record Phone(String countryPrefix, String number) {}

    public record Address(String address, String city, String countryCode, String state) {}

    public record Sanctions(boolean riskFlag, List<Hit> hits) {

        public record Hit(
                String name,
                Integer score,
                String sourceList,
                String category,
                String matchType,
                String reason) {}
    }
}
