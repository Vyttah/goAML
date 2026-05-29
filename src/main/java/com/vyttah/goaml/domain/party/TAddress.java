package com.vyttah.goaml.domain.party;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlType;

/**
 * goAML {@code t_address} — schema-ordered fields modeled in snake_case on the wire.
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "t_address", propOrder = {
        "addressType", "address", "town", "city", "zip", "countryCode", "state", "comments"
})
public class TAddress {

    @XmlElement(name = "address_type")
    private String addressType;

    @XmlElement(name = "address")
    private String address;

    @XmlElement(name = "town")
    private String town;

    @XmlElement(name = "city")
    private String city;

    @XmlElement(name = "zip")
    private String zip;

    @XmlElement(name = "country_code")
    private String countryCode;

    @XmlElement(name = "state")
    private String state;

    @XmlElement(name = "comments")
    private String comments;

    public String getAddressType() { return addressType; }
    public void setAddressType(String v) { this.addressType = v; }
    public String getAddress() { return address; }
    public void setAddress(String v) { this.address = v; }
    public String getTown() { return town; }
    public void setTown(String v) { this.town = v; }
    public String getCity() { return city; }
    public void setCity(String v) { this.city = v; }
    public String getZip() { return zip; }
    public void setZip(String v) { this.zip = v; }
    public String getCountryCode() { return countryCode; }
    public void setCountryCode(String v) { this.countryCode = v; }
    public String getState() { return state; }
    public void setState(String v) { this.state = v; }
    public String getComments() { return comments; }
    public void setComments(String v) { this.comments = v; }
}
