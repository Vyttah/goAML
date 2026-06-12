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
public final class ScreeningPartyMapper {

    private static final String CUSTOMER = "Customer";
    private static final String SHAREHOLDER = "Shareholder";
    private static final String UBO = "Beneficial Owner";
    private static final String DIRECTOR_ROLE = "Director";
    private static final int MAX_HITS_LISTED = 5;

    private ScreeningPartyMapper() {
    }

    /** The customer's display name (legal name / "first last"); never blank. */
    public static String displayName(ScreeningPartyPayload p) {
        if (p.subjectType() == ScreeningPartyPayload.SubjectType.LEGAL) {
            return p.legal() != null && notBlank(p.legal().legalName()) ? p.legal().legalName() : "Unknown";
        }
        if (p.natural() != null) {
            String name = (orEmpty(p.natural().firstName()) + " " + orEmpty(p.natural().lastName())).trim();
            return name.isEmpty() ? "Unknown" : name;
        }
        return "Unknown";
    }

    /** The customer party (first) followed by shareholder + UBO parties. Never empty. */
    public static List<DpmsrCreateRequest.Party> toParties(ScreeningPartyPayload p) {
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
            // No legal payload: emit only the directors we have. The entity name is the single mandatory
            // t_entity field, so an absent name yields INVALID (caught by the XSD gate, never filed) rather
            // than a fabricated "Unknown" reaching the FIU (B4: omit, do not fabricate).
            return new DpmsrCreateRequest.Entity(
                    null, null, null, null, null, null, directors(p.directors()), null, null, null, null);
        }
        return new DpmsrCreateRequest.Entity(
                emptyToNull(c.legalName()),
                c.commercialName(),
                firstNonBlank(c.incorporationNumber(), c.licenseNumber()),
                c.incorporationState(),
                c.incorporationCountry(),
                phone(c.phone()),
                directors(p.directors()),
                // B4 — previously had no slot and were dropped: carry them through (omitted when absent).
                toOffset(c.dateOfIncorporation()),
                c.trn(),
                null,
                address(c.address()));
    }

    private static DpmsrCreateRequest.Person naturalPerson(ScreeningPartyPayload.NaturalCustomer c) {
        if (c == null) {
            // No natural payload at all: omit everything except a placeholder gender. first/last name are
            // mandatory on t_person, so this yields INVALID (gate-caught) rather than a fabricated name.
            return new DpmsrCreateRequest.Person(genderCode(null), null, null,
                    null, null, null, null, null, null, null, null, null, null, null, null);
        }
        String idNumber = firstNonBlank(c.emiratesId(), firstIdNumber(c.identifications()));
        // B4: omit when absent — no countryOfBirth←nationality / residence←nationality substitution, no
        // name→"Unknown" fabrication. The lenient t_person keeps the XML valid with these fields omitted.
        return new DpmsrCreateRequest.Person(
                genderCode(c.gender()), emptyToNull(c.firstName()), emptyToNull(c.lastName()), toOffset(c.dob()),
                c.countryOfBirth(), c.nationality(), c.residence(),
                idNumber, null, c.occupation(), phone(c.phone()), address(c.address()),
                identifications(c.identifications()), c.email(), c.alias());
    }

    /** Maps the screening identifications onto the curated identifications (omitted when none). */
    private static List<DpmsrCreateRequest.Identification> identifications(
            List<ScreeningPartyPayload.Identification> ids) {
        List<ScreeningPartyPayload.Identification> list = nullSafe(ids);
        if (list.isEmpty()) {
            return null;
        }
        List<DpmsrCreateRequest.Identification> out = new ArrayList<>();
        for (ScreeningPartyPayload.Identification id : list) {
            out.add(new DpmsrCreateRequest.Identification(
                    id.type(), id.number(), toOffset(id.issueDate()), toOffset(id.expiryDate()),
                    id.issueCountry()));
        }
        return out;
    }

    private static DpmsrCreateRequest.Party relatedParty(ScreeningPartyPayload.RelatedParty rp, String reason) {
        String comments = relatedComments(rp);
        if ("LEGAL".equalsIgnoreCase(rp.partyType())) {
            // B4: omit when absent (no "Unknown"); the entity name is mandatory, so a truly nameless related
            // entity yields INVALID at the gate rather than a fabricated name in the filed XML.
            DpmsrCreateRequest.Entity entity = new DpmsrCreateRequest.Entity(
                    firstNonBlank(rp.legalName(), rp.fullName()), null,
                    rp.incorporationNumber(), null, rp.incorporationCountry(), phone(rp.phone()), null);
            return new DpmsrCreateRequest.Party(reason, comments, entity, null);
        }
        String[] name = names(rp.fullName(), rp.firstName(), rp.lastName());
        // B4: omit countryOfBirth (no nationality substitution) and residence when absent.
        DpmsrCreateRequest.Person person = new DpmsrCreateRequest.Person(
                genderCode(null), name[0], name[1], toOffset(rp.dob()), null, rp.nationality(),
                rp.residence(), rp.idNumber(), null, null, phone(rp.phone()), null, null);
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
                    genderCode(null), name[0], name[1], toOffset(d.dob()), null, null,
                    d.idNumber(), d.nationality(), d.residence(), DIRECTOR_ROLE, phone(d.phone())));
        }
        return out;
    }

    /** A human-readable PEP + sanctions summary for the customer party's comments; null when clean. */
    public static String sanctionsContext(ScreeningPartyPayload p) {
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

    /**
     * {first, last}; splits {@code fullName} when explicit names are blank. B4: <b>omits</b> (null) what is
     * genuinely absent rather than fabricating "Unknown" — first/last are mandatory on {@code t_person}, so a
     * truly nameless party yields INVALID at the gate (never filed) instead of a fabricated name in the FIU XML.
     */
    private static String[] names(String fullName, String firstName, String lastName) {
        if (notBlank(firstName)) {
            return new String[]{firstName, emptyToNull(lastName)};
        }
        if (notBlank(fullName)) {
            String[] parts = fullName.trim().split("\\s+", 2);
            return new String[]{parts[0], parts.length > 1 ? parts[1] : null};
        }
        return new String[]{null, null};
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

    /**
     * A valid goAML {@code gender_type} code ({@code M} / {@code F} / {@code -}). Party persons
     * ({@code t_person_my_client}) require gender, so an absent/unknown value maps to {@code "-"} ("not
     * provided") rather than being omitted (which fails XSD validation).
     */
    private static String genderCode(String gender) {
        if (gender == null) {
            return "-";
        }
        String g = gender.trim().toUpperCase();
        return ("M".equals(g) || "F".equals(g)) ? g : "-";
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

    private static String orEmpty(String s) {
        return s == null ? "" : s;
    }

    private static String blankToUnknown(String s) {
        return notBlank(s) ? s : "Unknown";
    }

    /** Null for an absent/blank value — used everywhere B4 chooses omission over fabrication. */
    private static String emptyToNull(String s) {
        return notBlank(s) ? s : null;
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
