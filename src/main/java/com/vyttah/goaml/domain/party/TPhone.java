package com.vyttah.goaml.domain.party;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlType;

/**
 * goAML {@code t_phone}.
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "t_phone", propOrder = {
        "contactType", "communicationType", "countryPrefix", "number", "extension", "comments"
})
public class TPhone {

    @XmlElement(name = "tph_contact_type")
    private String contactType;

    @XmlElement(name = "tph_communication_type")
    private String communicationType;

    @XmlElement(name = "tph_country_prefix")
    private String countryPrefix;

    @XmlElement(name = "tph_number")
    private String number;

    @XmlElement(name = "tph_extension")
    private String extension;

    @XmlElement(name = "comments")
    private String comments;

    public String getContactType() { return contactType; }
    public void setContactType(String v) { this.contactType = v; }
    public String getCommunicationType() { return communicationType; }
    public void setCommunicationType(String v) { this.communicationType = v; }
    public String getCountryPrefix() { return countryPrefix; }
    public void setCountryPrefix(String v) { this.countryPrefix = v; }
    public String getNumber() { return number; }
    public void setNumber(String v) { this.number = v; }
    public String getExtension() { return extension; }
    public void setExtension(String v) { this.extension = v; }
    public String getComments() { return comments; }
    public void setComments(String v) { this.comments = v; }
}
