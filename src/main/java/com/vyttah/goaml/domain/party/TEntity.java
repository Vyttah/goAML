package com.vyttah.goaml.domain.party;

import com.vyttah.goaml.domain.adapter.GoamlDateTimeAdapter;
import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlElementWrapper;
import jakarta.xml.bind.annotation.XmlType;
import jakarta.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * goAML {@code t_entity} — corporate / legal entity. Minimal Phase 3 surface
 * (name, registration, location); expand as engine builders need.
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "t_entity", propOrder = {
        "name", "commercialName", "incorporationLegalForm", "incorporationNumber",
        "business", "phones", "addresses", "email", "url",
        "incorporationState", "incorporationCountryCode", "incorporationDate",
        "taxNumber", "comments"
})
public class TEntity {

    @XmlElement(name = "name") private String name;
    @XmlElement(name = "commercial_name") private String commercialName;
    @XmlElement(name = "incorporation_legal_form") private String incorporationLegalForm;
    @XmlElement(name = "incorporation_number") private String incorporationNumber;
    @XmlElement(name = "business") private String business;

    @XmlElementWrapper(name = "phones")
    @XmlElement(name = "phone")
    private List<TPhone> phones = new ArrayList<>();

    @XmlElementWrapper(name = "addresses")
    @XmlElement(name = "address")
    private List<TAddress> addresses = new ArrayList<>();

    @XmlElement(name = "email") private String email;
    @XmlElement(name = "url") private String url;
    @XmlElement(name = "incorporation_state") private String incorporationState;
    @XmlElement(name = "incorporation_country_code") private String incorporationCountryCode;
    @XmlElement(name = "incorporation_date") @XmlJavaTypeAdapter(GoamlDateTimeAdapter.class)
    private OffsetDateTime incorporationDate;
    @XmlElement(name = "tax_number") private String taxNumber;
    @XmlElement(name = "comments") private String comments;

    public String getName() { return name; }
    public void setName(String v) { this.name = v; }
    public String getCommercialName() { return commercialName; }
    public void setCommercialName(String v) { this.commercialName = v; }
    public String getIncorporationLegalForm() { return incorporationLegalForm; }
    public void setIncorporationLegalForm(String v) { this.incorporationLegalForm = v; }
    public String getIncorporationNumber() { return incorporationNumber; }
    public void setIncorporationNumber(String v) { this.incorporationNumber = v; }
    public String getBusiness() { return business; }
    public void setBusiness(String v) { this.business = v; }
    public List<TPhone> getPhones() { return phones; }
    public void setPhones(List<TPhone> v) { this.phones = v == null ? new ArrayList<>() : new ArrayList<>(v); }
    public List<TAddress> getAddresses() { return addresses; }
    public void setAddresses(List<TAddress> v) { this.addresses = v == null ? new ArrayList<>() : new ArrayList<>(v); }
    public String getEmail() { return email; }
    public void setEmail(String v) { this.email = v; }
    public String getUrl() { return url; }
    public void setUrl(String v) { this.url = v; }
    public String getIncorporationState() { return incorporationState; }
    public void setIncorporationState(String v) { this.incorporationState = v; }
    public String getIncorporationCountryCode() { return incorporationCountryCode; }
    public void setIncorporationCountryCode(String v) { this.incorporationCountryCode = v; }
    public OffsetDateTime getIncorporationDate() { return incorporationDate; }
    public void setIncorporationDate(OffsetDateTime v) { this.incorporationDate = v; }
    public String getTaxNumber() { return taxNumber; }
    public void setTaxNumber(String v) { this.taxNumber = v; }
    public String getComments() { return comments; }
    public void setComments(String v) { this.comments = v; }
}
