package com.vyttah.goaml.domain;

import com.vyttah.goaml.domain.activity.Activity;
import com.vyttah.goaml.domain.adapter.GoamlDateTimeAdapter;
import com.vyttah.goaml.domain.common.ReportIndicator;
import com.vyttah.goaml.domain.common.ReportingPerson;
import com.vyttah.goaml.domain.enums.ReportCode;
import com.vyttah.goaml.domain.enums.SubmissionCode;
import com.vyttah.goaml.domain.party.TAddress;
import com.vyttah.goaml.domain.transaction.Transaction;
import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlElementWrapper;
import jakarta.xml.bind.annotation.XmlRootElement;
import jakarta.xml.bind.annotation.XmlType;
import jakarta.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Root of a goAML v4.0 submission — the {@code <report>} element. A report contains
 * <strong>either</strong> {@code transaction}(s) or an {@code activity}, never both,
 * keyed by the {@code report_code} (Phase 5 validation enforces this).
 */
@XmlRootElement(name = "report")
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "report", propOrder = {
        "rentityId", "rentityBranch", "submissionCode", "reportCode",
        "entityReference", "fiuRefNumber", "submissionDate", "currencyCodeLocal",
        "reportingPerson", "location", "reason", "action",
        "transactions",
        "activity",
        "reportIndicators"
})
public class Report {

    @XmlElement(name = "rentity_id") private Integer rentityId;
    @XmlElement(name = "rentity_branch") private String rentityBranch;

    @XmlElement(name = "submission_code") private SubmissionCode submissionCode;
    @XmlElement(name = "report_code") private ReportCode reportCode;

    @XmlElement(name = "entity_reference") private String entityReference;
    @XmlElement(name = "fiu_ref_number") private String fiuRefNumber;

    @XmlElement(name = "submission_date")
    @XmlJavaTypeAdapter(GoamlDateTimeAdapter.class)
    private OffsetDateTime submissionDate;

    @XmlElement(name = "currency_code_local") private String currencyCodeLocal;

    @XmlElement(name = "reporting_person") private ReportingPerson reportingPerson;
    @XmlElement(name = "location") private TAddress location;

    @XmlElement(name = "reason") private String reason;
    @XmlElement(name = "action") private String action;

    @XmlElement(name = "transaction")
    private List<Transaction> transactions = new ArrayList<>();

    @XmlElement(name = "activity") private Activity activity;

    @XmlElementWrapper(name = "report_indicators")
    @XmlElement(name = "indicator")
    private List<ReportIndicator> reportIndicators = new ArrayList<>();

    public Integer getRentityId() { return rentityId; }
    public void setRentityId(Integer v) { this.rentityId = v; }
    public String getRentityBranch() { return rentityBranch; }
    public void setRentityBranch(String v) { this.rentityBranch = v; }
    public SubmissionCode getSubmissionCode() { return submissionCode; }
    public void setSubmissionCode(SubmissionCode v) { this.submissionCode = v; }
    public ReportCode getReportCode() { return reportCode; }
    public void setReportCode(ReportCode v) { this.reportCode = v; }
    public String getEntityReference() { return entityReference; }
    public void setEntityReference(String v) { this.entityReference = v; }
    public String getFiuRefNumber() { return fiuRefNumber; }
    public void setFiuRefNumber(String v) { this.fiuRefNumber = v; }
    public OffsetDateTime getSubmissionDate() { return submissionDate; }
    public void setSubmissionDate(OffsetDateTime v) { this.submissionDate = v; }
    public String getCurrencyCodeLocal() { return currencyCodeLocal; }
    public void setCurrencyCodeLocal(String v) { this.currencyCodeLocal = v; }
    public ReportingPerson getReportingPerson() { return reportingPerson; }
    public void setReportingPerson(ReportingPerson v) { this.reportingPerson = v; }
    public TAddress getLocation() { return location; }
    public void setLocation(TAddress v) { this.location = v; }
    public String getReason() { return reason; }
    public void setReason(String v) { this.reason = v; }
    public String getAction() { return action; }
    public void setAction(String v) { this.action = v; }
    public List<Transaction> getTransactions() { return transactions; }
    public void setTransactions(List<Transaction> v) {
        this.transactions = v == null ? new ArrayList<>() : new ArrayList<>(v);
    }
    public Activity getActivity() { return activity; }
    public void setActivity(Activity v) { this.activity = v; }
    public List<ReportIndicator> getReportIndicators() { return reportIndicators; }
    public void setReportIndicators(List<ReportIndicator> v) {
        this.reportIndicators = v == null ? new ArrayList<>() : new ArrayList<>(v);
    }
}
