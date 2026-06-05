package com.vyttah.goaml.engine.build;

import com.vyttah.goaml.domain.generated.ReportPartyType;
import com.vyttah.goaml.domain.generated.TAccount;
import com.vyttah.goaml.domain.generated.TAccountMyClient;
import com.vyttah.goaml.domain.generated.TEntity;
import com.vyttah.goaml.domain.generated.TEntityMyClient;
import com.vyttah.goaml.domain.generated.TPerson;
import com.vyttah.goaml.domain.generated.TPersonMyClient;

/**
 * Wraps any of the six {@code report_party_type} subject kinds into a {@link ReportPartyType}, with the common
 * {@code reason}/{@code comments} set. The returned object is further customizable (e.g. {@code setRole},
 * {@code setSignificance}, {@code setCountry}, {@code setIsSuspected}) — full coverage, no field is hidden.
 */
public final class GoamlParties {

    private GoamlParties() {}

    public static ReportPartyType entity(TEntity entity, String reason, String comments) {
        ReportPartyType party = base(reason, comments);
        party.setEntity(entity);
        return party;
    }

    public static ReportPartyType entityMyClient(TEntityMyClient entity, String reason, String comments) {
        ReportPartyType party = base(reason, comments);
        party.setEntityMyClient(entity);
        return party;
    }

    public static ReportPartyType person(TPerson person, String reason, String comments) {
        ReportPartyType party = base(reason, comments);
        party.setPerson(person);
        return party;
    }

    public static ReportPartyType personMyClient(TPersonMyClient person, String reason, String comments) {
        ReportPartyType party = base(reason, comments);
        party.setPersonMyClient(person);
        return party;
    }

    public static ReportPartyType account(TAccount account, String reason, String comments) {
        ReportPartyType party = base(reason, comments);
        party.setAccount(account);
        return party;
    }

    public static ReportPartyType accountMyClient(TAccountMyClient account, String reason, String comments) {
        ReportPartyType party = base(reason, comments);
        party.setAccountMyClient(account);
        return party;
    }

    private static ReportPartyType base(String reason, String comments) {
        ReportPartyType party = new ReportPartyType();
        party.setReason(reason);
        party.setComments(comments);
        return party;
    }
}
