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
 * One row per {@code parties[]} entry whose subject is a natural person ({@code person}/
 * {@code personMyClient}) — a relational mirror of {@link Report#getInput()} alongside it, not a
 * replacement. See {@code .planning/migrate-json-storage-to-sql.md}.
 */
@Getter
@Setter
@Entity
@Table(name = "dpmsr_involved_party_natural")
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InvolvedPartyNatural {

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

    @Column(name = "first_name")
    private String firstName;
    @Column(name = "middle_name")
    private String middleName;
    @Column(name = "last_name")
    private String lastName;
    private String gender;
    private String title;
    private String prefix;
    private OffsetDateTime birthdate;
    @Column(name = "birth_place")
    private String birthPlace;
    @Column(name = "mothers_name")
    private String mothersName;
    private String alias;
    private String ssn;
    @Column(name = "id_number")
    private String idNumber;
    @Column(name = "passport_number")
    private String passportNumber;
    @Column(name = "passport_country")
    private String passportCountry;
    private String nationality1;
    private String nationality2;
    private String nationality3;
    private String residence;
    private String occupation;
    @Column(name = "employer_name")
    private String employerName;
    private Boolean deceased;
    @Column(name = "date_deceased")
    private OffsetDateTime dateDeceased;
    @Column(name = "tax_number")
    private String taxNumber;
    @Column(name = "tax_reg_number")
    private String taxRegNumber;
    @Column(name = "source_of_wealth")
    private String sourceOfWealth;

    @Column(name = "primary_phone_number")
    private String primaryPhoneNumber;
    @Column(name = "primary_address_line")
    private String primaryAddressLine;
    @Column(name = "primary_address_city")
    private String primaryAddressCity;
    @Column(name = "primary_address_country_code")
    private String primaryAddressCountryCode;
}
