package com.vyttah.goaml.model.dto.report;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
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
 *
 * <p>{@code clientMetadata} (A3) is an <b>optional</b> opaque JSON object a caller may attach (the AML
 * cockpit's captured-not-filed LiveExShield-parity fields). It is persisted verbatim to its own
 * {@code report.client_metadata} column and returned in the detail view, but is <b>never</b> mapped onto the
 * engine input (see {@link #toInput} consumers) and so never reaches the marshalled goAML XML.
 */
public record DpmsrCreateRequest(
        String rentityBranch,
        @NotBlank String entityReference,
        @NotNull @JsonDeserialize(using = LenientOffsetDateTimeDeserializer.class) OffsetDateTime submissionDate,
        String fiuRefNumber,
        String reason,
        String action,
        List<String> indicators,
        Person reportingPerson,
        Address location,
        @NotNull List<Party> parties,
        @NotNull List<Goods> goods,
        JsonNode clientMetadata) {

    /**
     * Convenience constructor without {@code clientMetadata} (defaults to {@code null}) — keeps the many
     * internal feeds (CSV / accounting / screening / tests / MLRO-injection) that never carry caller metadata
     * concise. The full canonical constructor is used by the JSON-binding create endpoint.
     */
    public DpmsrCreateRequest(
            String rentityBranch, String entityReference, OffsetDateTime submissionDate, String fiuRefNumber,
            String reason, String action, List<String> indicators, Person reportingPerson, Address location,
            List<Party> parties, List<Goods> goods) {
        this(rentityBranch, entityReference, submissionDate, fiuRefNumber, reason, action, indicators,
                reportingPerson, location, parties, goods, null);
    }

    /** A report party — exactly one of {@link #entity} or {@link #person} should be set. */
    public record Party(String reason, String comments, Entity entity, Person person) {}

    public record Entity(
            @NotBlank String name,
            String commercialName,
            String incorporationNumber,
            String incorporationState,
            String incorporationCountryCode,
            Phone phone,
            List<Director> directors,
            // B4 — carry-through fields the screening curated path previously had no slot for (so they were
            // silently dropped). All optional on the lenient t_entity; set-when-present, never fabricated.
            @JsonDeserialize(using = LenientOffsetDateTimeDeserializer.class) OffsetDateTime incorporationDate,
            String taxRegNumber,
            String business,
            Address address) {

        /** Backward-compatible constructor (no B4 carry-through fields) for the many existing call sites. */
        public Entity(String name, String commercialName, String incorporationNumber, String incorporationState,
                      String incorporationCountryCode, Phone phone, List<Director> directors) {
            this(name, commercialName, incorporationNumber, incorporationState, incorporationCountryCode,
                    phone, directors, null, null, null, null);
        }
    }

    public record Director(
            String gender,
            @NotBlank String firstName,
            @NotBlank String lastName,
            @JsonDeserialize(using = LenientOffsetDateTimeDeserializer.class) OffsetDateTime birthdate,
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
            @JsonDeserialize(using = LenientOffsetDateTimeDeserializer.class) OffsetDateTime birthdate,
            String countryOfBirth,
            String nationality,
            String residence,
            String idNumber,
            String taxRegNumber,
            String occupation,
            Phone phone,
            Address address,
            List<Identification> identifications,
            // B4 — carry-through fields the screening curated path previously dropped. Optional on lenient
            // t_person; set-when-present.
            String email,
            String alias) {

        /** Backward-compatible constructor (no email/alias) for the many existing call sites. */
        public Person(String gender, String firstName, String lastName, OffsetDateTime birthdate,
                      String countryOfBirth, String nationality, String residence, String idNumber,
                      String taxRegNumber, String occupation, Phone phone, Address address,
                      List<Identification> identifications) {
            this(gender, firstName, lastName, birthdate, countryOfBirth, nationality, residence, idNumber,
                    taxRegNumber, occupation, phone, address, identifications, null, null);
        }
    }

    public record Identification(
            String type,
            String number,
            @JsonDeserialize(using = LenientOffsetDateTimeDeserializer.class) OffsetDateTime issueDate,
            @JsonDeserialize(using = LenientOffsetDateTimeDeserializer.class) OffsetDateTime expiryDate,
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
            @JsonDeserialize(using = LenientOffsetDateTimeDeserializer.class) OffsetDateTime registrationDate,
            BigDecimal disposedValue,
            String statusComments,
            String registrationNumber,
            String identificationNumber) {}
}
