package com.vyttah.goaml.domain.transaction;

import com.vyttah.goaml.domain.party.TAccountMyClient;
import com.vyttah.goaml.domain.party.TEntityMyClient;
import com.vyttah.goaml.domain.party.TPersonMyClient;
import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlType;

/**
 * goAML {@code t_from_my_client} — the "source side" of a bi-party transaction when the
 * source is a client of the reporting entity. Exactly one of {@code fromAccount},
 * {@code fromPerson}, or {@code fromEntity} must be set.
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "t_from_my_client", propOrder = {
        "fromFundsCode", "fromFundsComment", "fromForeignCurrency",
        "fromAccount", "fromPerson", "fromEntity", "fromCountry"
})
public class TFromMyClient {

    @XmlElement(name = "from_funds_code") private String fromFundsCode;
    @XmlElement(name = "from_funds_comment") private String fromFundsComment;
    @XmlElement(name = "from_foreign_currency") private TForeignCurrency fromForeignCurrency;

    @XmlElement(name = "from_account") private TAccountMyClient fromAccount;
    @XmlElement(name = "from_person") private TPersonMyClient fromPerson;
    @XmlElement(name = "from_entity") private TEntityMyClient fromEntity;

    @XmlElement(name = "from_country") private String fromCountry;

    public String getFromFundsCode() { return fromFundsCode; }
    public void setFromFundsCode(String v) { this.fromFundsCode = v; }
    public String getFromFundsComment() { return fromFundsComment; }
    public void setFromFundsComment(String v) { this.fromFundsComment = v; }
    public TForeignCurrency getFromForeignCurrency() { return fromForeignCurrency; }
    public void setFromForeignCurrency(TForeignCurrency v) { this.fromForeignCurrency = v; }
    public TAccountMyClient getFromAccount() { return fromAccount; }
    public void setFromAccount(TAccountMyClient v) { this.fromAccount = v; }
    public TPersonMyClient getFromPerson() { return fromPerson; }
    public void setFromPerson(TPersonMyClient v) { this.fromPerson = v; }
    public TEntityMyClient getFromEntity() { return fromEntity; }
    public void setFromEntity(TEntityMyClient v) { this.fromEntity = v; }
    public String getFromCountry() { return fromCountry; }
    public void setFromCountry(String v) { this.fromCountry = v; }
}
