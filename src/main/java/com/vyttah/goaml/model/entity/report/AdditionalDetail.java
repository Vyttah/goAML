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
 * Optional/overflow DPMSR fields that don't get their own column — repeatable sub-lists (phones,
 * addresses, emails, director IDs, sanctions, related persons/entities, network devices, entity
 * identifications, URLs) and whole account-type parties ({@code category = ACCOUNT_PARTY}, which stays
 * out of the 5-table budget per {@code .planning/migrate-json-storage-to-sql.md} §6a Q5). {@code data} is
 * a raw JSON string, Jackson-serialized straight from the JAXB leaf object — same pattern as
 * {@link Report#getInput()}.
 */
@Getter
@Setter
@Entity
@Table(name = "dpmsr_additional_details")
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdditionalDetail {

    @Id
    private UUID id;

    @Column(name = "report_id")
    private UUID reportId;

    @Column(name = "goods_id")
    private UUID goodsId;

    @Column(name = "natural_party_id")
    private UUID naturalPartyId;

    @Column(name = "legal_party_id")
    private UUID legalPartyId;

    private String category;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String data;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;
}
