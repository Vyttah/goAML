package com.vyttah.goaml.domain.activity;

import com.vyttah.goaml.domain.party.TPerson;
import com.vyttah.goaml.domain.party.TPersonMyClient;
import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlType;

/**
 * goAML {@code report_party} — a subject involved in an activity-based report. Exactly one of
 * {person, person_my_client, account, account_my_client, entity, entity_my_client} must be set.
 * Phase 3 ships the two person variants; account/entity variants land in Phase 4 when the
 * transaction-shape types are added.
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "report_party_type", propOrder = {
        "person", "personMyClient", "significance", "reason", "comments"
})
public class ReportParty {

    @XmlElement(name = "person") private TPerson person;
    @XmlElement(name = "person_my_client") private TPersonMyClient personMyClient;

    @XmlElement(name = "significance") private Integer significance;
    @XmlElement(name = "reason") private String reason;
    @XmlElement(name = "comments") private String comments;

    public TPerson getPerson() { return person; }
    public void setPerson(TPerson v) { this.person = v; }
    public TPersonMyClient getPersonMyClient() { return personMyClient; }
    public void setPersonMyClient(TPersonMyClient v) { this.personMyClient = v; }
    public Integer getSignificance() { return significance; }
    public void setSignificance(Integer v) { this.significance = v; }
    public String getReason() { return reason; }
    public void setReason(String v) { this.reason = v; }
    public String getComments() { return comments; }
    public void setComments(String v) { this.comments = v; }
}
