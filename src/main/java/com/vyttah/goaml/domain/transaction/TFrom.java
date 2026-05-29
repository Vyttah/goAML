package com.vyttah.goaml.domain.transaction;

import com.vyttah.goaml.domain.party.TAccount;
import com.vyttah.goaml.domain.party.TEntity;
import com.vyttah.goaml.domain.party.TPerson;
import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlType;

/**
 * goAML {@code t_from} — the "source side" of a bi-party transaction when the source is
 * external to the reporting entity. Exactly one of {@code fromAccount}, {@code fromPerson},
 * or {@code fromEntity} must be set (validation in Phase 5).
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "t_from", propOrder = {
        "fromFundsCode", "fromFundsComment", "fromForeignCurrency",
        "fromAccount", "fromPerson", "fromEntity", "fromCountry"
})
public class TFrom {

    @XmlElement(name = "from_funds_code") private String fromFundsCode;
    @XmlElement(name = "from_funds_comment") private String fromFundsComment;
    @XmlElement(name = "from_foreign_currency") private TForeignCurrency fromForeignCurrency;

    @XmlElement(name = "from_account") private TAccount fromAccount;
    @XmlElement(name = "from_person") private TPerson fromPerson;
    @XmlElement(name = "from_entity") private TEntity fromEntity;

    @XmlElement(name = "from_country") private String fromCountry;

    public String getFromFundsCode() { return fromFundsCode; }
    public void setFromFundsCode(String v) { this.fromFundsCode = v; }
    public String getFromFundsComment() { return fromFundsComment; }
    public void setFromFundsComment(String v) { this.fromFundsComment = v; }
    public TForeignCurrency getFromForeignCurrency() { return fromForeignCurrency; }
    public void setFromForeignCurrency(TForeignCurrency v) { this.fromForeignCurrency = v; }
    public TAccount getFromAccount() { return fromAccount; }
    public void setFromAccount(TAccount v) { this.fromAccount = v; }
    public TPerson getFromPerson() { return fromPerson; }
    public void setFromPerson(TPerson v) { this.fromPerson = v; }
    public TEntity getFromEntity() { return fromEntity; }
    public void setFromEntity(TEntity v) { this.fromEntity = v; }
    public String getFromCountry() { return fromCountry; }
    public void setFromCountry(String v) { this.fromCountry = v; }
}
