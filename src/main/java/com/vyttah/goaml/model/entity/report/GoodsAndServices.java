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

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * One row per {@code goods[]} item on a DPMSR report — a relational mirror of {@link Report#getInput()}
 * alongside it, not a replacement. See {@code .planning/migrate-json-storage-to-sql.md}.
 */
@Getter
@Setter
@Entity
@Table(name = "dpmsr_goods_and_services")
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GoodsAndServices {

    @Id
    private UUID id;

    @Column(name = "report_id")
    private UUID reportId;

    private Integer ordinal;

    @Column(name = "item_type")
    private String itemType;
    @Column(name = "item_make")
    private String itemMake;
    @Column(length = 8000)
    private String description;
    @Column(name = "previously_registered_to")
    private String previouslyRegisteredTo;
    @Column(name = "presently_registered_to")
    private String presentlyRegisteredTo;
    @Column(name = "estimated_value")
    private BigDecimal estimatedValue;
    @Column(name = "status_code")
    private String statusCode;
    @Column(name = "status_comments")
    private String statusComments;
    @Column(name = "disposed_value")
    private BigDecimal disposedValue;
    @Column(name = "currency_code")
    private String currencyCode;
    private BigDecimal size;
    @Column(name = "size_uom")
    private String sizeUom;
    @Column(name = "registration_date")
    private OffsetDateTime registrationDate;
    @Column(name = "registration_number")
    private String registrationNumber;
    @Column(name = "identification_number")
    private String identificationNumber;
    @Column(length = 8000)
    private String comments;

    @Column(name = "address_line")
    private String addressLine;
    @Column(name = "address_city")
    private String addressCity;
    @Column(name = "address_state")
    private String addressState;
    @Column(name = "address_country_code")
    private String addressCountryCode;
    @Column(name = "address_zip")
    private String addressZip;
    @Column(name = "address_type")
    private String addressType;
}
