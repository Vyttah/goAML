package com.vyttah.goaml.domain.transaction;

import com.vyttah.goaml.domain.party.TAccount;
import com.vyttah.goaml.domain.party.TEntity;
import com.vyttah.goaml.domain.party.TPerson;
import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlType;

/**
 * goAML {@code t_to} — the "destination side" of a bi-party transaction when the destination
 * is external to the reporting entity. Exactly one of {@code toAccount}, {@code toPerson},
 * or {@code toEntity} must be set.
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "t_to", propOrder = {
        "toFundsCode", "toFundsComment", "toForeignCurrency",
        "toAccount", "toPerson", "toEntity", "toCountry"
})
public class TTo {

    @XmlElement(name = "to_funds_code") private String toFundsCode;
    @XmlElement(name = "to_funds_comment") private String toFundsComment;
    @XmlElement(name = "to_foreign_currency") private TForeignCurrency toForeignCurrency;

    @XmlElement(name = "to_account") private TAccount toAccount;
    @XmlElement(name = "to_person") private TPerson toPerson;
    @XmlElement(name = "to_entity") private TEntity toEntity;

    @XmlElement(name = "to_country") private String toCountry;

    public String getToFundsCode() { return toFundsCode; }
    public void setToFundsCode(String v) { this.toFundsCode = v; }
    public String getToFundsComment() { return toFundsComment; }
    public void setToFundsComment(String v) { this.toFundsComment = v; }
    public TForeignCurrency getToForeignCurrency() { return toForeignCurrency; }
    public void setToForeignCurrency(TForeignCurrency v) { this.toForeignCurrency = v; }
    public TAccount getToAccount() { return toAccount; }
    public void setToAccount(TAccount v) { this.toAccount = v; }
    public TPerson getToPerson() { return toPerson; }
    public void setToPerson(TPerson v) { this.toPerson = v; }
    public TEntity getToEntity() { return toEntity; }
    public void setToEntity(TEntity v) { this.toEntity = v; }
    public String getToCountry() { return toCountry; }
    public void setToCountry(String v) { this.toCountry = v; }
}
