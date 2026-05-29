package com.vyttah.goaml.domain.party;

import com.vyttah.goaml.domain.adapter.GoamlDateTimeAdapter;
import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlType;
import jakarta.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

import java.time.OffsetDateTime;

/**
 * goAML {@code t_person_identification} — an ID document (passport, Emirates ID, etc.).
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "t_person_identification", propOrder = {
        "type", "number", "issueDate", "expiryDate", "issuedBy", "issueCountry", "comments"
})
public class TPersonIdentification {

    @XmlElement(name = "type")
    private String type;

    @XmlElement(name = "number")
    private String number;

    @XmlElement(name = "issue_date")
    @XmlJavaTypeAdapter(GoamlDateTimeAdapter.class)
    private OffsetDateTime issueDate;

    @XmlElement(name = "expiry_date")
    @XmlJavaTypeAdapter(GoamlDateTimeAdapter.class)
    private OffsetDateTime expiryDate;

    @XmlElement(name = "issued_by")
    private String issuedBy;

    @XmlElement(name = "issue_country")
    private String issueCountry;

    @XmlElement(name = "comments")
    private String comments;

    public String getType() { return type; }
    public void setType(String v) { this.type = v; }
    public String getNumber() { return number; }
    public void setNumber(String v) { this.number = v; }
    public OffsetDateTime getIssueDate() { return issueDate; }
    public void setIssueDate(OffsetDateTime v) { this.issueDate = v; }
    public OffsetDateTime getExpiryDate() { return expiryDate; }
    public void setExpiryDate(OffsetDateTime v) { this.expiryDate = v; }
    public String getIssuedBy() { return issuedBy; }
    public void setIssuedBy(String v) { this.issuedBy = v; }
    public String getIssueCountry() { return issueCountry; }
    public void setIssueCountry(String v) { this.issueCountry = v; }
    public String getComments() { return comments; }
    public void setComments(String v) { this.comments = v; }
}
