package com.vyttah.goaml.domain.party;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlType;

/**
 * goAML {@code t_account} — minimal Phase 3 surface (institution + account number + label).
 * Additional fields (SWIFT, IBAN, signatories, balance, etc.) are added in Phase 4 as the
 * engine builders need them.
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "t_account", propOrder = {
        "institutionName", "swift", "branch", "account", "currencyCode",
        "accountName", "iban", "comments"
})
public class TAccount {

    @XmlElement(name = "institution_name") private String institutionName;
    @XmlElement(name = "swift") private String swift;
    @XmlElement(name = "branch") private String branch;
    @XmlElement(name = "account") private String account;
    @XmlElement(name = "currency_code") private String currencyCode;
    @XmlElement(name = "account_name") private String accountName;
    @XmlElement(name = "iban") private String iban;
    @XmlElement(name = "comments") private String comments;

    public String getInstitutionName() { return institutionName; }
    public void setInstitutionName(String v) { this.institutionName = v; }
    public String getSwift() { return swift; }
    public void setSwift(String v) { this.swift = v; }
    public String getBranch() { return branch; }
    public void setBranch(String v) { this.branch = v; }
    public String getAccount() { return account; }
    public void setAccount(String v) { this.account = v; }
    public String getCurrencyCode() { return currencyCode; }
    public void setCurrencyCode(String v) { this.currencyCode = v; }
    public String getAccountName() { return accountName; }
    public void setAccountName(String v) { this.accountName = v; }
    public String getIban() { return iban; }
    public void setIban(String v) { this.iban = v; }
    public String getComments() { return comments; }
    public void setComments(String v) { this.comments = v; }
}
