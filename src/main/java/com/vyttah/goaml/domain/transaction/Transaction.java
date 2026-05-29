package com.vyttah.goaml.domain.transaction;

import com.vyttah.goaml.domain.adapter.GoamlDateTimeAdapter;
import com.vyttah.goaml.domain.common.GoodsServices;
import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlType;
import jakarta.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * goAML {@code <transaction>} — the transaction-based report shape (STR / AIFT / ECDDT).
 *
 * <p>A transaction is either <strong>bi-party</strong> (exactly one of
 * {@code tFromMyClient}/{@code tFrom} <em>plus</em> exactly one of
 * {@code tToMyClient}/{@code tTo}) <strong>or</strong> <strong>multi-party</strong>
 * (a list of {@link TParty} with explicit roles, new in schema v4.0). Validation enforces
 * the choice; the POJO carries fields for both so the engine builders can populate either.
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "transaction", propOrder = {
        "transactionNumber", "internalRefNumber",
        "transactionLocation", "transactionDescription",
        "dateTransaction",
        "teller", "authorized",
        "lateDeposit", "datePosting", "valueDate",
        "transmodeCode", "transmodeComment",
        "amountLocal",
        "tFromMyClient", "tFrom",
        "tToMyClient", "tTo",
        "tParties",
        "goodsServices"
})
public class Transaction {

    @XmlElement(name = "transactionnumber") private String transactionNumber;
    @XmlElement(name = "internal_ref_number") private String internalRefNumber;
    @XmlElement(name = "transaction_location") private String transactionLocation;
    @XmlElement(name = "transaction_description") private String transactionDescription;

    @XmlElement(name = "date_transaction") @XmlJavaTypeAdapter(GoamlDateTimeAdapter.class)
    private OffsetDateTime dateTransaction;

    @XmlElement(name = "teller") private String teller;
    @XmlElement(name = "authorized") private String authorized;

    @XmlElement(name = "late_deposit") private Boolean lateDeposit;
    @XmlElement(name = "date_posting") @XmlJavaTypeAdapter(GoamlDateTimeAdapter.class)
    private OffsetDateTime datePosting;
    @XmlElement(name = "value_date") @XmlJavaTypeAdapter(GoamlDateTimeAdapter.class)
    private OffsetDateTime valueDate;

    @XmlElement(name = "transmode_code") private String transmodeCode;
    @XmlElement(name = "transmode_comment") private String transmodeComment;

    @XmlElement(name = "amount_local") private BigDecimal amountLocal;

    // Bi-party: exactly one from-side + one to-side.
    @XmlElement(name = "t_from_my_client") private TFromMyClient tFromMyClient;
    @XmlElement(name = "t_from") private TFrom tFrom;
    @XmlElement(name = "t_to_my_client") private TToMyClient tToMyClient;
    @XmlElement(name = "t_to") private TTo tTo;

    // Multi-party (schema v4.0): list of parties with roles.
    @XmlElement(name = "t_party") private List<TParty> tParties = new ArrayList<>();

    @XmlElement(name = "goods_services") private List<GoodsServices> goodsServices = new ArrayList<>();

    public String getTransactionNumber() { return transactionNumber; }
    public void setTransactionNumber(String v) { this.transactionNumber = v; }
    public String getInternalRefNumber() { return internalRefNumber; }
    public void setInternalRefNumber(String v) { this.internalRefNumber = v; }
    public String getTransactionLocation() { return transactionLocation; }
    public void setTransactionLocation(String v) { this.transactionLocation = v; }
    public String getTransactionDescription() { return transactionDescription; }
    public void setTransactionDescription(String v) { this.transactionDescription = v; }
    public OffsetDateTime getDateTransaction() { return dateTransaction; }
    public void setDateTransaction(OffsetDateTime v) { this.dateTransaction = v; }
    public String getTeller() { return teller; }
    public void setTeller(String v) { this.teller = v; }
    public String getAuthorized() { return authorized; }
    public void setAuthorized(String v) { this.authorized = v; }
    public Boolean getLateDeposit() { return lateDeposit; }
    public void setLateDeposit(Boolean v) { this.lateDeposit = v; }
    public OffsetDateTime getDatePosting() { return datePosting; }
    public void setDatePosting(OffsetDateTime v) { this.datePosting = v; }
    public OffsetDateTime getValueDate() { return valueDate; }
    public void setValueDate(OffsetDateTime v) { this.valueDate = v; }
    public String getTransmodeCode() { return transmodeCode; }
    public void setTransmodeCode(String v) { this.transmodeCode = v; }
    public String getTransmodeComment() { return transmodeComment; }
    public void setTransmodeComment(String v) { this.transmodeComment = v; }
    public BigDecimal getAmountLocal() { return amountLocal; }
    public void setAmountLocal(BigDecimal v) { this.amountLocal = v; }
    public TFromMyClient getTFromMyClient() { return tFromMyClient; }
    public void setTFromMyClient(TFromMyClient v) { this.tFromMyClient = v; }
    public TFrom getTFrom() { return tFrom; }
    public void setTFrom(TFrom v) { this.tFrom = v; }
    public TToMyClient getTToMyClient() { return tToMyClient; }
    public void setTToMyClient(TToMyClient v) { this.tToMyClient = v; }
    public TTo getTTo() { return tTo; }
    public void setTTo(TTo v) { this.tTo = v; }
    public List<TParty> getTParties() { return tParties; }
    public void setTParties(List<TParty> v) { this.tParties = v == null ? new ArrayList<>() : new ArrayList<>(v); }
    public List<GoodsServices> getGoodsServices() { return goodsServices; }
    public void setGoodsServices(List<GoodsServices> v) {
        this.goodsServices = v == null ? new ArrayList<>() : new ArrayList<>(v);
    }
}
