package com.vyttah.goaml.domain.transaction;

import com.vyttah.goaml.domain.party.TAccountMyClient;
import com.vyttah.goaml.domain.party.TEntityMyClient;
import com.vyttah.goaml.domain.party.TPersonMyClient;
import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlType;

/**
 * goAML {@code t_to_my_client} — the "destination side" of a bi-party transaction when the
 * destination is a client of the reporting entity.
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "t_to_my_client", propOrder = {
        "toFundsCode", "toFundsComment", "toForeignCurrency",
        "toAccount", "toPerson", "toEntity", "toCountry"
})
public class TToMyClient {

    @XmlElement(name = "to_funds_code") private String toFundsCode;
    @XmlElement(name = "to_funds_comment") private String toFundsComment;
    @XmlElement(name = "to_foreign_currency") private TForeignCurrency toForeignCurrency;

    @XmlElement(name = "to_account") private TAccountMyClient toAccount;
    @XmlElement(name = "to_person") private TPersonMyClient toPerson;
    @XmlElement(name = "to_entity") private TEntityMyClient toEntity;

    @XmlElement(name = "to_country") private String toCountry;

    public String getToFundsCode() { return toFundsCode; }
    public void setToFundsCode(String v) { this.toFundsCode = v; }
    public String getToFundsComment() { return toFundsComment; }
    public void setToFundsComment(String v) { this.toFundsComment = v; }
    public TForeignCurrency getToForeignCurrency() { return toForeignCurrency; }
    public void setToForeignCurrency(TForeignCurrency v) { this.toForeignCurrency = v; }
    public TAccountMyClient getToAccount() { return toAccount; }
    public void setToAccount(TAccountMyClient v) { this.toAccount = v; }
    public TPersonMyClient getToPerson() { return toPerson; }
    public void setToPerson(TPersonMyClient v) { this.toPerson = v; }
    public TEntityMyClient getToEntity() { return toEntity; }
    public void setToEntity(TEntityMyClient v) { this.toEntity = v; }
    public String getToCountry() { return toCountry; }
    public void setToCountry(String v) { this.toCountry = v; }
}
