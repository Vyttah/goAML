package com.vyttah.goaml.domain.transaction;

import com.vyttah.goaml.domain.party.TAccount;
import com.vyttah.goaml.domain.party.TAccountMyClient;
import com.vyttah.goaml.domain.party.TEntity;
import com.vyttah.goaml.domain.party.TEntityMyClient;
import com.vyttah.goaml.domain.party.TPerson;
import com.vyttah.goaml.domain.party.TPersonMyClient;
import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlType;

/**
 * goAML {@code t_party} — used for multi-party transactions (new in schema v4.0). Each
 * party carries a role (Buyer/Seller/…) and exactly one of the six subject variants.
 * Validation in Phase 5 enforces the "exactly one subject" rule.
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "t_party", propOrder = {
        "role",
        "person", "personMyClient", "account", "accountMyClient", "entity", "entityMyClient",
        "fundsCode", "fundsComment", "foreignCurrency", "country", "significance", "comments"
})
public class TParty {

    @XmlElement(name = "role") private String role;

    @XmlElement(name = "person") private TPerson person;
    @XmlElement(name = "person_my_client") private TPersonMyClient personMyClient;
    @XmlElement(name = "account") private TAccount account;
    @XmlElement(name = "account_my_client") private TAccountMyClient accountMyClient;
    @XmlElement(name = "entity") private TEntity entity;
    @XmlElement(name = "entity_my_client") private TEntityMyClient entityMyClient;

    @XmlElement(name = "funds_code") private String fundsCode;
    @XmlElement(name = "funds_comment") private String fundsComment;
    @XmlElement(name = "foreign_currency") private TForeignCurrency foreignCurrency;
    @XmlElement(name = "country") private String country;
    @XmlElement(name = "significance") private Integer significance;
    @XmlElement(name = "comments") private String comments;

    public String getRole() { return role; }
    public void setRole(String v) { this.role = v; }
    public TPerson getPerson() { return person; }
    public void setPerson(TPerson v) { this.person = v; }
    public TPersonMyClient getPersonMyClient() { return personMyClient; }
    public void setPersonMyClient(TPersonMyClient v) { this.personMyClient = v; }
    public TAccount getAccount() { return account; }
    public void setAccount(TAccount v) { this.account = v; }
    public TAccountMyClient getAccountMyClient() { return accountMyClient; }
    public void setAccountMyClient(TAccountMyClient v) { this.accountMyClient = v; }
    public TEntity getEntity() { return entity; }
    public void setEntity(TEntity v) { this.entity = v; }
    public TEntityMyClient getEntityMyClient() { return entityMyClient; }
    public void setEntityMyClient(TEntityMyClient v) { this.entityMyClient = v; }
    public String getFundsCode() { return fundsCode; }
    public void setFundsCode(String v) { this.fundsCode = v; }
    public String getFundsComment() { return fundsComment; }
    public void setFundsComment(String v) { this.fundsComment = v; }
    public TForeignCurrency getForeignCurrency() { return foreignCurrency; }
    public void setForeignCurrency(TForeignCurrency v) { this.foreignCurrency = v; }
    public String getCountry() { return country; }
    public void setCountry(String v) { this.country = v; }
    public Integer getSignificance() { return significance; }
    public void setSignificance(Integer v) { this.significance = v; }
    public String getComments() { return comments; }
    public void setComments(String v) { this.comments = v; }
}
