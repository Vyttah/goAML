package com.vyttah.goaml.engine.build;

import com.vyttah.goaml.domain.generated.ReportPartyType;
import com.vyttah.goaml.domain.generated.TAddress;
import com.vyttah.goaml.domain.generated.TPersonRegistrationInReport;
import com.vyttah.goaml.domain.generated.TTransItem;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Compact, schema-driven input for a DPMSR report — the single contract consumed by
 * {@link DpmsrReportBuilder}. It stays thin by carrying the <em>generated leaf types</em>
 * ({@link TPersonRegistrationInReport}, {@link ReportPartyType}, {@link TTransItem}) for the rich nested
 * objects, so every DPMSR/activity field is reachable without a parallel DTO model.
 *
 * <p>Build it directly (programmatic callers — the RabbitMQ consumer, the plugin) or via the fluent
 * {@link #builder()} (hand-coding / tests). Parties are built with {@link GoamlParties} (all 6 subjects);
 * the DPMSR-fixed header values (submission_code {@code E}, report_code {@code DPMSR}, currency {@code AED})
 * are applied by the builder, not carried here.
 */
public record DpmsrReportInput(
        int rentityId,
        String rentityBranch,
        String entityReference,
        OffsetDateTime submissionDate,
        String fiuRefNumber,
        TPersonRegistrationInReport reportingPerson,
        TAddress location,
        String reason,
        String action,
        List<String> indicators,
        List<ReportPartyType> parties,
        List<TTransItem> goods) {

    public static Builder builder() {
        return new Builder();
    }

    /** Fluent constructor for {@link DpmsrReportInput}. */
    public static final class Builder {
        private int rentityId;
        private String rentityBranch;
        private String entityReference;
        private OffsetDateTime submissionDate;
        private String fiuRefNumber;
        private TPersonRegistrationInReport reportingPerson;
        private TAddress location;
        private String reason;
        private String action;
        private final List<String> indicators = new ArrayList<>();
        private final List<ReportPartyType> parties = new ArrayList<>();
        private final List<TTransItem> goods = new ArrayList<>();

        public Builder rentityId(int v) { this.rentityId = v; return this; }
        public Builder rentityBranch(String v) { this.rentityBranch = v; return this; }
        public Builder entityReference(String v) { this.entityReference = v; return this; }
        public Builder submissionDate(OffsetDateTime v) { this.submissionDate = v; return this; }
        public Builder fiuRefNumber(String v) { this.fiuRefNumber = v; return this; }
        public Builder reportingPerson(TPersonRegistrationInReport v) { this.reportingPerson = v; return this; }
        public Builder location(TAddress v) { this.location = v; return this; }
        public Builder reason(String v) { this.reason = v; return this; }
        public Builder action(String v) { this.action = v; return this; }

        public Builder indicators(String... codes) {
            this.indicators.addAll(List.of(codes));
            return this;
        }

        public Builder party(ReportPartyType party) {
            this.parties.add(party);
            return this;
        }

        public Builder parties(ReportPartyType... parties) {
            this.parties.addAll(List.of(parties));
            return this;
        }

        public Builder goods(TTransItem... items) {
            this.goods.addAll(List.of(items));
            return this;
        }

        public DpmsrReportInput build() {
            return new DpmsrReportInput(rentityId, rentityBranch, entityReference, submissionDate, fiuRefNumber,
                    reportingPerson, location, reason, action,
                    List.copyOf(indicators), List.copyOf(parties), List.copyOf(goods));
        }
    }
}
