package com.vyttah.goaml.model.dto.report;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * The JSON contract for creating a DPMSR report — a curated, Jackson-clean shape that the
 * {@code DpmsrRequestMapper} maps onto the engine's {@code DpmsrReportInput} (generated leaf types). It is
 * persisted verbatim as the report's {@code input} JSONB so a report can be re-validated / re-edited.
 *
 * <p>Covers the fields a real DPMSR filing needs (entity/person parties, directors, goods, the reporting
 * MLRO, location, indicators). DPMSR-fixed header values (submission_code {@code E}, report_code
 * {@code DPMSR}, currency {@code AED}) and {@code rentity_id} are applied by the engine / from
 * {@code tenant_goaml_config} — not carried here.
 *
 * <p>{@code reportingPerson} is <b>optional</b>: when omitted, the service injects the tenant's active goAML
 * person ({@code tenant_goaml_person}) so the AML cockpit / CSV / accounting / screening feeds need not send
 * the MLRO. If none is configured and none is supplied, the report validates as INVALID
 * ({@code reporting_person is mandatory}).
 */
public record DpmsrCreateRequest(
        String rentityBranch,
        @NotBlank String entityReference,
        @NotNull OffsetDateTime submissionDate,
        String fiuRefNumber,
        String reason,
        String action,
        List<String> indicators,
        Person reportingPerson,
        Address location,
        @NotNull List<Party> parties,
        @NotNull List<Goods> goods) {

    /** A report party — exactly one of {@link #entity} or {@link #person} should be set. */
    public record Party(String reason, String comments, Entity entity, Person person) {}

    public record Entity(
            @NotBlank String name,
            String commercialName,
            String incorporationNumber,
            String incorporationState,
            String incorporationCountryCode,
            Phone phone,
            List<Director> directors) {}

    public record Director(
            String gender,
            @NotBlank String firstName,
            @NotBlank String lastName,
            OffsetDateTime birthdate,
            String passportNumber,
            String passportCountry,
            String idNumber,
            String nationality,
            String residence,
            String role,
            Phone phone) {}

    public record Person(
            String gender,
            @NotBlank String firstName,
            @NotBlank String lastName,
            OffsetDateTime birthdate,
            String countryOfBirth,
            String nationality,
            String residence,
            String idNumber,
            String taxRegNumber,
            String occupation,
            Phone phone,
            Address address,
            List<Identification> identifications) {}

    public record Identification(
            String type,
            String number,
            OffsetDateTime issueDate,
            OffsetDateTime expiryDate,
            String issueCountry) {}

    public record Phone(String contactType, String communicationType, String countryPrefix, String number) {}

    public record Address(String addressType, String address, String city, String countryCode, String state) {}

    public record Goods(
            @NotBlank String itemType,
            String itemMake,
            String description,
            String presentlyRegisteredTo,
            String statusCode,
            @NotNull BigDecimal estimatedValue,
            String currencyCode,
            BigDecimal size,
            String sizeUom,
            OffsetDateTime registrationDate,
            BigDecimal disposedValue,
            String statusComments,
            String registrationNumber,
            String identificationNumber) {}
}
