package com.vyttah.goaml.service.integration;

import com.vyttah.goaml.model.dto.integration.ScreeningPartyPayload;
import com.vyttah.goaml.model.dto.report.DpmsrCreateRequest;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

/**
 * Maps a {@link ScreeningPartyPayload} (an already-resolved screened customer) onto a goAML party set
 * ({@link DpmsrCreateRequest.Party}) — Phase 1.5c. The customer becomes the first party (legal → Entity with
 * its directors; natural → Person with its identifications); shareholders and UBOs become additional parties.
 * The sanctions/PEP verdict is recorded as the customer party's {@code comments} (goAML's {@code item_type}-style
 * indicator codes need the pending FIU lookups, so we record context as free text rather than guess a code).
 *
 * <p>Kept in the integration layer so screening vocabulary stays out of the engine, exactly like
 * {@code AccountingDpmsrMapper}.
 */
final class ScreeningPartyMapper {

    private static final String CUSTOMER = "Customer";
    private static final String SHAREHOLDER = "Shareholder";
    private static final String UBO = "Beneficial Owner";
    private static final String DIRECTOR_ROLE = "Director";
    private static final int MAX_HITS_LISTED = 5;

    private ScreeningPartyMapper() {
    }

    /** The customer party (first) followed by shareholder + UBO parties. Never empty. */
    static List<DpmsrCreateRequest.Party> toParties(ScreeningPartyPayload p) {
        List<DpmsrCreateRequest.Party> parties = new ArrayList<>();
        parties.add(customerParty(p));
        for (ScreeningPartyPayload.RelatedParty sh : nullSafe(p.shareholders())) {
            parties.add(relatedParty(sh, SHAREHOLDER));
        }
        for (ScreeningPartyPayload.RelatedParty ubo : nullSafe(p.ubos())) {
            parties.add(relatedParty(ubo, UBO));
        }
        return parties;
    }

    private static DpmsrCreateRequest.Party customerParty(ScreeningPartyPayload p) {
        String comments = sanctionsContext(p);
        if (p.subjectType() == ScreeningPartyPayload.SubjectType.LEGAL) {
            return new DpmsrCreateRequest.Party(CUSTOMER, comments, legalEntity(p), null);
        }
        return new DpmsrCreateRequest.Party(CUSTOMER, comments, null, naturalPerson(p.natural()));
    }

    private static DpmsrCreateRequest.Entity legalEntity(ScreeningPartyPayload p) {
        ScreeningPartyPayload.LegalCustomer c = p.legal();
        if (c == null) {
            return new DpmsrCreateRequest.Entity("Unknown", null, null, null, null, null, directors(p.directors()));
        }
        return new DpmsrCreateRequest.Entity(
                blankToUnknown(c.legalName()),
                c.commercialName(),
                firstNonBlank(c.incorporationNumber(), c.licenseNumber()),
                c.incorporationState(),
                c.incorporationCountry(),
                phone(c.phone()),
                directors(p.directors()));
    }

    private static DpmsrCreateRequest.Person naturalPerson(ScreeningPartyPayload.NaturalCustomer c) {
        if (c == null) {
            return new DpmsrCreateRequest.Person(null, "Unknown", "Unknown",
                    null, null, null, null, null, null, null, null, null);
        }
        String[] name = names(null, c.firstName(), c.lastName());
        String idNumber = firstNonBlank(c.emiratesId(), firstIdNumber(c.identifications()));
        return new DpmsrCreateRequest.Person(
                c.gender(), name[0], name[1], toOffset(c.dob()), c.nationality(), c.residence(),
                idNumber, null, c.occupation(), phone(c.phone()), address(c.address()),
                identifications(c.identifications()));
    }

    private static DpmsrCreateRequest.Party relatedParty(ScreeningPartyPayload.RelatedParty rp, String reason) {
        String comments = relatedComments(rp);
        if ("LEGAL".equalsIgnoreCase(rp.partyType())) {
            DpmsrCreateRequest.Entity entity = new DpmsrCreateRequest.Entity(
                    firstNonBlank(rp.legalName(), rp.fullName(), "Unknown"), null,
                    rp.incorporationNumber(), null, rp.incorporationCountry(), phone(rp.phone()), null);
            return new DpmsrCreateRequest.Party(reason, comments, entity, null);
        }
        String[] name = names(rp.fullName(), rp.firstName(), rp.lastName());
        DpmsrCreateRequest.Person person = new DpmsrCreateRequest.Person(
                null, name[0], name[1], toOffset(rp.dob()), rp.nationality(), rp.residence(),
                rp.idNumber(), null, null, phone(rp.phone()), null,
                rp.idType() != null || rp.idNumber() != null
                        ? List.of(new DpmsrCreateRequest.Identification(
                                rp.idType(), rp.idNumber(), null, null, rp.idCountry()))
                        : null);
        return new DpmsrCreateRequest.Party(reason, comments, null, person);
    }

    private static List<DpmsrCreateRequest.Director> directors(List<ScreeningPartyPayload.RelatedParty> src) {
        List<ScreeningPartyPayload.RelatedParty> list = nullSafe(src);
        if (list.isEmpty()) {
            return null;
        }
        List<DpmsrCreateRequest.Director> out = new ArrayList<>();
        for (ScreeningPartyPayload.RelatedParty d : list) {
            String[] name = names(d.fullName(), d.firstName(), d.lastName());
            out.add(new DpmsrCreateRequest.Director(
                    null, name[0], name[1], toOffset(d.dob()), null, null,
                    d.idNumber(), d.nationality(), d.residence(), DIRECTOR_ROLE, phone(d.phone())));
        }
        return out;
    }

    private static List<DpmsrCreateRequest.Identification> identifications(
            List<ScreeningPartyPayload.Identification> src) {
        List<ScreeningPartyPayload.Identification> list = nullSafe(src);
        if (list.isEmpty()) {
            return null;
        }
        return list.stream()
                .map(i -> new DpmsrCreateRequest.Identification(
                        i.type(), i.number(), toOffset(i.issueDate()), toOffset(i.expiryDate()), i.issueCountry()))
                .toList();
    }

    /** A human-readable PEP + sanctions summary for the customer party's comments; null when clean. */
    static String sanctionsContext(ScreeningPartyPayload p) {
        StringBuilder sb = new StringBuilder();
        boolean pep = p.subjectType() == ScreeningPartyPayload.SubjectType.NATURAL
                && p.natural() != null && p.natural().pep();
        if (pep) {
            sb.append("PEP. ");
        }
        ScreeningPartyPayload.Sanctions s = p.sanctions();
        if (s != null && (s.riskFlag() || !nullSafe(s.hits()).isEmpty())) {
            List<ScreeningPartyPayload.Sanctions.Hit> hits = nullSafe(s.hits());
            sb.append("Sanctions screening risk flagged");
            if (!hits.isEmpty()) {
                sb.append(" — ").append(hits.size()).append(" hit(s): ");
                List<String> rendered = new ArrayList<>();
                for (ScreeningPartyPayload.Sanctions.Hit h : hits.subList(0, Math.min(hits.size(), MAX_HITS_LISTED))) {
                    rendered.add(renderHit(h));
                }
                sb.append(String.join("; ", rendered));
                if (hits.size() > MAX_HITS_LISTED) {
                    sb.append("; +").append(hits.size() - MAX_HITS_LISTED).append(" more");
                }
            }
            sb.append('.');
        }
        String out = sb.toString().trim();
        return out.isEmpty() ? null : out;
    }

    private static String renderHit(ScreeningPartyPayload.Sanctions.Hit h) {
        StringBuilder b = new StringBuilder(blankToUnknown(h.name()));
        List<String> bits = new ArrayList<>();
        if (notBlank(h.sourceList())) bits.add(h.sourceList());
        if (h.score() != null) bits.add("score " + h.score());
        if (notBlank(h.category())) bits.add(h.category());
        if (!bits.isEmpty()) {
            b.append(" (").append(String.join(", ", bits)).append(')');
        }
        return b.toString();
    }

    private static String relatedComments(ScreeningPartyPayload.RelatedParty rp) {
        List<String> bits = new ArrayList<>();
        if (rp.shareholdingPercent() != null) {
            bits.add(rp.shareholdingPercent().stripTrailingZeros().toPlainString() + "% shareholding");
        }
        if (rp.pep()) {
            bits.add("PEP");
        }
        return bits.isEmpty() ? null : String.join("; ", bits);
    }

    // ----- small helpers -----

    /** {first, last}; splits {@code fullName} when explicit names are blank; never blank (→ "Unknown"). */
    private static String[] names(String fullName, String firstName, String lastName) {
        if (notBlank(firstName)) {
            return new String[]{firstName, notBlank(lastName) ? lastName : "Unknown"};
        }
        if (notBlank(fullName)) {
            String[] parts = fullName.trim().split("\\s+", 2);
            return new String[]{parts[0], parts.length > 1 ? parts[1] : "Unknown"};
        }
        return new String[]{"Unknown", "Unknown"};
    }

    private static String firstIdNumber(List<ScreeningPartyPayload.Identification> ids) {
        List<ScreeningPartyPayload.Identification> list = nullSafe(ids);
        return list.isEmpty() ? null : list.get(0).number();
    }

    private static DpmsrCreateRequest.Phone phone(ScreeningPartyPayload.Phone p) {
        return p == null ? null : new DpmsrCreateRequest.Phone(null, null, p.countryPrefix(), p.number());
    }

    private static DpmsrCreateRequest.Address address(ScreeningPartyPayload.Address a) {
        return a == null ? null
                : new DpmsrCreateRequest.Address(null, a.address(), a.city(), a.countryCode(), a.state());
    }

    private static OffsetDateTime toOffset(LocalDate date) {
        return date == null ? null : date.atStartOfDay().atOffset(ZoneOffset.UTC);
    }

    private static <T> List<T> nullSafe(List<T> list) {
        return list == null ? List.of() : list;
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    private static String blankToUnknown(String s) {
        return notBlank(s) ? s : "Unknown";
    }

    private static String firstNonBlank(String... values) {
        for (String v : values) {
            if (notBlank(v)) {
                return v;
            }
        }
        return null;
    }
}
