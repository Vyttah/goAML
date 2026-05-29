package com.vyttah.goaml.domain.activity;

import com.vyttah.goaml.domain.common.GoodsServices;
import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlElementWrapper;
import jakarta.xml.bind.annotation.XmlType;

import java.util.ArrayList;
import java.util.List;

/**
 * goAML {@code <activity>} — the activity-based report shape (SAR / AIF / ECDD / DPMSR).
 * Contains the involved parties and the goods/services that changed hands. Transactions live
 * in a separate {@code <transaction>} block on the report (see {@code com.vyttah.goaml.domain.transaction}).
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "activity", propOrder = {"reportParties", "goodsServices"})
public class Activity {

    @XmlElementWrapper(name = "report_parties")
    @XmlElement(name = "report_party")
    private List<ReportParty> reportParties = new ArrayList<>();

    @XmlElement(name = "goods_services")
    private List<GoodsServices> goodsServices = new ArrayList<>();

    public List<ReportParty> getReportParties() { return reportParties; }
    public void setReportParties(List<ReportParty> v) {
        this.reportParties = v == null ? new ArrayList<>() : new ArrayList<>(v);
    }

    public List<GoodsServices> getGoodsServices() { return goodsServices; }
    public void setGoodsServices(List<GoodsServices> v) {
        this.goodsServices = v == null ? new ArrayList<>() : new ArrayList<>(v);
    }
}
