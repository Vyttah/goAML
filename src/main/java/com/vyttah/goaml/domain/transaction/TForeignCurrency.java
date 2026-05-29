package com.vyttah.goaml.domain.transaction;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlType;

import java.math.BigDecimal;

/**
 * goAML {@code t_foreign_currency} — used when a transaction was conducted in something
 * other than the report's {@code currency_code_local}.
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "t_foreign_currency", propOrder = {
        "foreignCurrencyCode", "foreignAmount", "foreignExchangeRate"
})
public class TForeignCurrency {

    @XmlElement(name = "foreign_currency_code") private String foreignCurrencyCode;
    @XmlElement(name = "foreign_amount") private BigDecimal foreignAmount;
    @XmlElement(name = "foreign_exchange_rate") private BigDecimal foreignExchangeRate;

    public String getForeignCurrencyCode() { return foreignCurrencyCode; }
    public void setForeignCurrencyCode(String v) { this.foreignCurrencyCode = v; }
    public BigDecimal getForeignAmount() { return foreignAmount; }
    public void setForeignAmount(BigDecimal v) { this.foreignAmount = v; }
    public BigDecimal getForeignExchangeRate() { return foreignExchangeRate; }
    public void setForeignExchangeRate(BigDecimal v) { this.foreignExchangeRate = v; }
}
