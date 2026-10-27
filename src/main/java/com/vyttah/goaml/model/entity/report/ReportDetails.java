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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * The DPMSR report header/scalar section (1:1 with {@link Report}) — {@code action}, {@code reason},
 * {@code location}, {@code indicators}, {@code fiuRefNumber}, {@code rentityBranch}, {@code submissionDate},
 * and {@code reportingPerson}. A relational mirror of {@link Report#getInput()} alongside it, not a
 * replacement — see {@code .planning/migrate-json-storage-to-sql.md}.
 *
 * <p>XSD-"required" fields (e.g. {@code reportingPersonFirstName}, {@code locationAddress}) are nullable
 * here even though the goAML schema marks them mandatory: a report can be stored as an in-progress
 * DRAFT/INVALID row before those fields are filled in — that's enforced by the existing validation gate,
 * not by this table.
 */
@Getter
@Setter
@Entity
@Table(name = "dpmsr_report_details")
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReportDetails {

    @Id
    @Column(name = "report_id")
    private UUID reportId;

    @Column(length = 8000)
    private String action;

    @Column(length = 8000)
    private String reason;

    @Column(name = "fiu_ref_number")
    private String fiuRefNumber;

    @Column(name = "rentity_branch")
    private String rentityBranch;

    @Column(name = "submission_date")
    private OffsetDateTime submissionDate;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "indicators", columnDefinition = "varchar(5)[]")
    private String[] indicators;

    @Column(name = "location_address")
    private String locationAddress;
    @Column(name = "location_house_number")
    private String locationHouseNumber;
    @Column(name = "location_apartment_number")
    private String locationApartmentNumber;
    @Column(name = "location_additional_line1")
    private String locationAdditionalLine1;
    @Column(name = "location_additional_line2")
    private String locationAdditionalLine2;
    @Column(name = "location_town")
    private String locationTown;
    @Column(name = "location_city")
    private String locationCity;
    @Column(name = "location_state")
    private String locationState;
    @Column(name = "location_zip")
    private String locationZip;
    @Column(name = "location_country_code")
    private String locationCountryCode;
    @Column(name = "location_address_type")
    private String locationAddressType;
    @Column(name = "location_comments", length = 8000)
    private String locationComments;

    @Column(name = "reporting_person_first_name")
    private String reportingPersonFirstName;
    @Column(name = "reporting_person_middle_name")
    private String reportingPersonMiddleName;
    @Column(name = "reporting_person_last_name")
    private String reportingPersonLastName;
    @Column(name = "reporting_person_gender")
    private String reportingPersonGender;
    @Column(name = "reporting_person_title")
    private String reportingPersonTitle;
    @Column(name = "reporting_person_prefix")
    private String reportingPersonPrefix;
    @Column(name = "reporting_person_birthdate")
    private OffsetDateTime reportingPersonBirthdate;
    @Column(name = "reporting_person_birth_place")
    private String reportingPersonBirthPlace;
    @Column(name = "reporting_person_mothers_name")
    private String reportingPersonMothersName;
    @Column(name = "reporting_person_alias")
    private String reportingPersonAlias;
    @Column(name = "reporting_person_ssn")
    private String reportingPersonSsn;
    @Column(name = "reporting_person_id_number")
    private String reportingPersonIdNumber;
    @Column(name = "reporting_person_passport_number")
    private String reportingPersonPassportNumber;
    @Column(name = "reporting_person_passport_country")
    private String reportingPersonPassportCountry;
    @Column(name = "reporting_person_nationality1")
    private String reportingPersonNationality1;
    @Column(name = "reporting_person_nationality2")
    private String reportingPersonNationality2;
    @Column(name = "reporting_person_nationality3")
    private String reportingPersonNationality3;
    @Column(name = "reporting_person_residence")
    private String reportingPersonResidence;
    @Column(name = "reporting_person_occupation")
    private String reportingPersonOccupation;
    @Column(name = "reporting_person_employer_name")
    private String reportingPersonEmployerName;
    @Column(name = "reporting_person_tax_number")
    private String reportingPersonTaxNumber;
    @Column(name = "reporting_person_tax_reg_number")
    private String reportingPersonTaxRegNumber;
    @Column(name = "reporting_person_source_of_wealth")
    private String reportingPersonSourceOfWealth;
    @Column(name = "reporting_person_deceased")
    private Boolean reportingPersonDeceased;
    @Column(name = "reporting_person_date_deceased")
    private OffsetDateTime reportingPersonDateDeceased;
    @Column(name = "reporting_person_comments", length = 8000)
    private String reportingPersonComments;
}
