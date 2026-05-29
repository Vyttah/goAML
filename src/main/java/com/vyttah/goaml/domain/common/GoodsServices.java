package com.vyttah.goaml.domain.common;

import com.vyttah.goaml.domain.adapter.GoamlDateTimeAdapter;
import com.vyttah.goaml.domain.party.TAddress;
import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlType;
import jakarta.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * goAML {@code goods_services} — the item changing hands. Central to DPMSR (gold, diamonds,
 * silver, etc.) and used by other report types where physical goods are involved.
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "goods_services", propOrder = {
        "itemType", "itemMake", "description",
        "previouslyRegisteredTo", "presentlyRegisteredTo",
        "estimatedValue", "statusCode", "statusComments", "disposedValue", "currencyCode",
        "size", "sizeUom",
        "address",
        "registrationDate", "registrationNumber", "identificationNumber",
        "comments"
})
public class GoodsServices {

    @XmlElement(name = "item_type") private String itemType;
    @XmlElement(name = "item_make") private String itemMake;
    @XmlElement(name = "description") private String description;
    @XmlElement(name = "previously_registered_to") private String previouslyRegisteredTo;
    @XmlElement(name = "presently_registered_to") private String presentlyRegisteredTo;
    @XmlElement(name = "estimated_value") private BigDecimal estimatedValue;
    @XmlElement(name = "status_code") private String statusCode;
    @XmlElement(name = "status_comments") private String statusComments;
    @XmlElement(name = "disposed_value") private BigDecimal disposedValue;
    @XmlElement(name = "currency_code") private String currencyCode;
    @XmlElement(name = "size") private BigDecimal size;
    @XmlElement(name = "size_uom") private String sizeUom;
    @XmlElement(name = "address") private TAddress address;
    @XmlElement(name = "registration_date") @XmlJavaTypeAdapter(GoamlDateTimeAdapter.class)
    private OffsetDateTime registrationDate;
    @XmlElement(name = "registration_number") private String registrationNumber;
    @XmlElement(name = "identification_number") private String identificationNumber;
    @XmlElement(name = "comments") private String comments;

    public String getItemType() { return itemType; }
    public void setItemType(String v) { this.itemType = v; }
    public String getItemMake() { return itemMake; }
    public void setItemMake(String v) { this.itemMake = v; }
    public String getDescription() { return description; }
    public void setDescription(String v) { this.description = v; }
    public String getPreviouslyRegisteredTo() { return previouslyRegisteredTo; }
    public void setPreviouslyRegisteredTo(String v) { this.previouslyRegisteredTo = v; }
    public String getPresentlyRegisteredTo() { return presentlyRegisteredTo; }
    public void setPresentlyRegisteredTo(String v) { this.presentlyRegisteredTo = v; }
    public BigDecimal getEstimatedValue() { return estimatedValue; }
    public void setEstimatedValue(BigDecimal v) { this.estimatedValue = v; }
    public String getStatusCode() { return statusCode; }
    public void setStatusCode(String v) { this.statusCode = v; }
    public String getStatusComments() { return statusComments; }
    public void setStatusComments(String v) { this.statusComments = v; }
    public BigDecimal getDisposedValue() { return disposedValue; }
    public void setDisposedValue(BigDecimal v) { this.disposedValue = v; }
    public String getCurrencyCode() { return currencyCode; }
    public void setCurrencyCode(String v) { this.currencyCode = v; }
    public BigDecimal getSize() { return size; }
    public void setSize(BigDecimal v) { this.size = v; }
    public String getSizeUom() { return sizeUom; }
    public void setSizeUom(String v) { this.sizeUom = v; }
    public TAddress getAddress() { return address; }
    public void setAddress(TAddress v) { this.address = v; }
    public OffsetDateTime getRegistrationDate() { return registrationDate; }
    public void setRegistrationDate(OffsetDateTime v) { this.registrationDate = v; }
    public String getRegistrationNumber() { return registrationNumber; }
    public void setRegistrationNumber(String v) { this.registrationNumber = v; }
    public String getIdentificationNumber() { return identificationNumber; }
    public void setIdentificationNumber(String v) { this.identificationNumber = v; }
    public String getComments() { return comments; }
    public void setComments(String v) { this.comments = v; }
}
