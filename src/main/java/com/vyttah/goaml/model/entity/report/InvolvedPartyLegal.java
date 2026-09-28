package com.vyttah.goaml.model.entity.report;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * One row per {@code parties[]} entry whose subject is a legal entity ({@code entity}/
 * {@code entityMyClient}) — a relational mirror of {@link Report#getInput()} alongside it, not a
 * replacement. See {@code .planning/migrate-json-storage-to-sql.md}.
 */
@Getter
@Setter
@Entity
@Table(name = "dpmsr_involved_party_legal")
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InvolvedPartyLegal {

    @Id
    private UUID id;

    @Column(name = "report_id")
    private UUID reportId;

    @Column(name = "party_ordinal")
    private Integer partyOrdinal;

    @Column(name = "is_my_client")
    private Boolean isMyClient;

    private String role;
    @Column(length = 8000)
    private String reason;
    private String country;
    @Column(length = 8000)
    private String comments;
    @Column(name = "is_suspected")
    private Boolean isSuspected;
    private Short significance;

    private String name;
    @Column(name = "commercial_name")
    private String commercialName;
    @Column(name = "incorporation_legal_form")
    private String incorporationLegalForm;
    @Column(name = "incorporation_number")
    private String incorporationNumber;
    private String business;
    @Column(name = "entity_status")
    private String entityStatus;
    @Column(name = "entity_status_date")
    private OffsetDateTime entityStatusDate;
    @Column(name = "incorporation_state")
    private String incorporationState;
    @Column(name = "incorporation_country_code")
    private String incorporationCountryCode;
    @Column(name = "incorporation_date")
    private OffsetDateTime incorporationDate;
    @Column(name = "business_closed")
    private Boolean businessClosed;
    @Column(name = "date_business_closed")
    private OffsetDateTime dateBusinessClosed;
    @Column(name = "tax_number")
    private String taxNumber;
    @Column(name = "tax_reg_number")
    private String taxRegNumber;

    @Column(name = "primary_phone_number")
    private String primaryPhoneNumber;
    @Column(name = "primary_address_line")
    private String primaryAddressLine;
    @Column(name = "primary_address_city")
    private String primaryAddressCity;
    @Column(name = "primary_address_country_code")
    private String primaryAddressCountryCode;
}
