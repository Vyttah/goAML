package com.vyttah.goaml.model.mapper.report;

import com.vyttah.goaml.domain.generated.CurrencyType;
import com.vyttah.goaml.domain.generated.EntityPersonRoleType;
import com.vyttah.goaml.domain.generated.ReportPartyType;
import com.vyttah.goaml.domain.generated.TAddress;
import com.vyttah.goaml.domain.generated.TEntity;
import com.vyttah.goaml.domain.generated.TPerson;
import com.vyttah.goaml.domain.generated.TPersonIdentification;
import com.vyttah.goaml.domain.generated.TPersonRegistrationInReport;
import com.vyttah.goaml.domain.generated.TPhone;
import com.vyttah.goaml.domain.generated.TTransItem;
import com.vyttah.goaml.engine.build.DpmsrReportInput;
import com.vyttah.goaml.engine.build.GoamlParties;
import com.vyttah.goaml.engine.build.GoamlWrappers;
import com.vyttah.goaml.engine.build.NameNormalizer;
import com.vyttah.goaml.engine.validation.ValidationMessage;
import com.vyttah.goaml.model.dto.report.DpmsrCreateRequest;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Maps the curated {@link DpmsrCreateRequest} JSON contract onto the engine's {@link DpmsrReportInput}
 * (generated JAXB leaf types). Hand-written rather than MapStruct because the target is the goAML
 * wrapper-per-owner JAXB model (each owner has its own {@code Phones}/{@code Addresses}/… class).
 * Only set-when-present, so optional fields don't emit empty elements.
 *
 * <p><strong>Name normalization is never silent</strong> (cardinal rule: never silently alter filed data).
 * {@code NameNormalizer} still rewrites XSD-illegal punctuation ({@code &}→and, strip {@code (),/}) so a
 * real UAE legal name stays XSD-valid — but every change is surfaced into the caller-visible message list:
 * a changed name emits a {@code NAME_NORMALIZED} WARNING (the original is preserved verbatim in the stored
 * {@code report.input}); a name the normalizer empties entirely emits a {@code NAME_UNREPRESENTABLE} ERROR
 * (clear failure instead of a raw XSD SAX error).
 */
@Component
public class DpmsrRequestMapper {

    /** Stable code for the WARNING emitted when normalization changed a filed name. */
    public static final String NAME_NORMALIZED = "NAME_NORMALIZED";
    /** Stable code for the ERROR emitted when normalization emptied a filed name. */
    public static final String NAME_UNREPRESENTABLE = "NAME_UNREPRESENTABLE";

    /** Back-compat entry point — normalization findings are collected but discarded. Prefer the overload. */
    public DpmsrReportInput toInput(DpmsrCreateRequest req, int rentityId) {
        return toInput(req, rentityId, new ArrayList<>());
    }

    /**
     * Map the request, appending any normalization findings (changed-name WARNINGs, emptied-name ERRORs)
     * to {@code messages} so the create/validate response surfaces them.
     */
    public DpmsrReportInput toInput(DpmsrCreateRequest req, int rentityId, List<ValidationMessage> messages) {
        DpmsrReportInput.Builder b = DpmsrReportInput.builder()
                .rentityId(rentityId)
                .rentityBranch(req.rentityBranch())
                .entityReference(req.entityReference())
                .submissionDate(req.submissionDate())
                .fiuRefNumber(req.fiuRefNumber())
                .reportingPerson(req.reportingPerson() != null
                        ? reportingPerson(req.reportingPerson(), messages) : null)
                .location(address(req.location()))
                .reason(req.reason())
                .action(req.action());

        if (req.indicators() != null) {
            b.indicators(req.indicators().toArray(String[]::new));
        }
        if (req.parties() != null) {
            int i = 0;
            for (DpmsrCreateRequest.Party p : req.parties()) {
                b.party(party(p, "parties[" + i + "]", messages));
                i++;
            }
        }
        if (req.goods() != null) {
            for (DpmsrCreateRequest.Goods g : req.goods()) {
                b.goods(goods(g));
            }
        }
        return b.build();
    }

    // ---------- parties ----------

    private ReportPartyType party(DpmsrCreateRequest.Party p, String path, List<ValidationMessage> messages) {
        if (p.entity() != null && p.person() != null) {
            // Never silently drop one side — a party with both is an ambiguous filing, refuse it.
            throw new IllegalArgumentException("party must have exactly one of person|entity (" + path + ")");
        }
        if (p.entity() != null) {
            return GoamlParties.entity(entity(p.entity(), path + ".entity", messages), p.reason(), p.comments());
        }
        if (p.person() != null) {
            return GoamlParties.person(person(p.person(), path + ".person", messages), p.reason(), p.comments());
        }
        throw new IllegalArgumentException("party must have an entity or a person (" + path + ")");
    }

    private TEntity entity(DpmsrCreateRequest.Entity dto, String path, List<ValidationMessage> messages) {
        TEntity e = new TEntity();
        // C8: normalize XSD-illegal punctuation (&, (), comma, /) in name fields so a real UAE legal name
        // stays XSD-valid instead of failing marshalling with a raw SAX pattern error. Surfaced, not silent.
        e.setName(normalizeName(dto.name(), path + ".name", messages));
        e.setCommercialName(normalizeName(dto.commercialName(), path + ".commercialName", messages));
        e.setIncorporationNumber(dto.incorporationNumber());
        e.setIncorporationState(dto.incorporationState());
        e.setIncorporationCountryCode(dto.incorporationCountryCode());
        // B4 carry-through (all optional on lenient t_entity; set-when-present, never fabricated).
        e.setIncorporationDate(dto.incorporationDate());
        e.setTaxRegNumber(dto.taxRegNumber());
        e.setBusiness(dto.business());
        if (dto.phone() != null) {
            e.setPhones(GoamlWrappers.wrap(new TEntity.Phones(), TEntity.Phones::getPhone, phone(dto.phone())));
        }
        if (dto.address() != null) {
            e.setAddresses(GoamlWrappers.wrap(
                    new TEntity.Addresses(), TEntity.Addresses::getAddress, address(dto.address())));
        }
        if (dto.directors() != null) {
            int i = 0;
            for (DpmsrCreateRequest.Director d : dto.directors()) {
                e.getDirectorId().add(director(d, path + ".directors[" + i + "]", messages));
                i++;
            }
        }
        return e;
    }

    private TEntity.DirectorId director(DpmsrCreateRequest.Director dto, String path,
                                        List<ValidationMessage> messages) {
        TEntity.DirectorId d = new TEntity.DirectorId();
        d.setGender(dto.gender());
        d.setFirstName(normalizeName(dto.firstName(), path + ".firstName", messages));
        d.setLastName(normalizeName(dto.lastName(), path + ".lastName", messages));
        d.setBirthdate(dto.birthdate());
        d.setPassportNumber(dto.passportNumber());
        d.setPassportCountry(dto.passportCountry());
        d.setIdNumber(dto.idNumber());
        d.setNationality1(dto.nationality());
        d.setResidence(dto.residence());
        if (dto.role() != null) {
            d.setRole(EntityPersonRoleType.fromValue(dto.role()));
        }
        if (dto.phone() != null) {
            d.setPhones(GoamlWrappers.wrap(new TPerson.Phones(), TPerson.Phones::getPhone, phone(dto.phone())));
        }
        return d;
    }

    /**
     * Maps a curated person party onto the <em>lenient</em> {@code t_person} ({@link TPerson}) — not
     * {@code t_person_my_client} (B5). On the lenient type only {@code first_name}/{@code last_name} are
     * mandatory, so a minimal feed (e.g. the CSV importer) can produce a VALID person report; the heavier
     * fields (gender, birthdate, id_number, nationality, residence, {@code tax_reg_number}, phone, address,
     * identifications) are <strong>set only when present</strong>, never fabricated. {@code tax_reg_number}
     * stays mappable but is optional here (vs. the 1-char-mandatory trap on my_client). The party
     * {@code address} (B17) is mapped via the {@code t_person} addresses wrapper — previously dropped.
     */
    private TPerson person(DpmsrCreateRequest.Person dto, String path, List<ValidationMessage> messages) {
        TPerson p = new TPerson();
        p.setGender(dto.gender());
        p.setFirstName(normalizeName(dto.firstName(), path + ".firstName", messages));
        p.setLastName(normalizeName(dto.lastName(), path + ".lastName", messages));
        p.setBirthdate(dto.birthdate());
        p.setCountryOfBirth(dto.countryOfBirth());
        p.setNationality1(dto.nationality());
        p.setResidence(dto.residence());
        p.setIdNumber(dto.idNumber());
        p.setTaxRegNumber(dto.taxRegNumber());
        p.setOccupation(dto.occupation());
        p.setAlias(dto.alias());
        // <emails> wraps a List<String>; emit only when an email is supplied (the email|emails choice is
        // optional on lenient t_person).
        if (dto.email() != null && !dto.email().isBlank()) {
            TPerson.Emails emails = new TPerson.Emails();
            emails.getEmail().add(dto.email());
            p.setEmails(emails);
        }
        // <phones> is optional on lenient t_person — only emit the wrapper when a phone is actually supplied.
        if (dto.phone() != null) {
            p.setPhones(GoamlWrappers.wrap(new TPerson.Phones(), TPerson.Phones::getPhone, phone(dto.phone())));
        }
        if (dto.address() != null) {
            p.setAddresses(GoamlWrappers.wrap(
                    new TPerson.Addresses(), TPerson.Addresses::getAddress, address(dto.address())));
        }
        if (dto.identifications() != null && !dto.identifications().isEmpty()) {
            TPerson.Identifications wrapper = new TPerson.Identifications();
            for (DpmsrCreateRequest.Identification id : dto.identifications()) {
                wrapper.getIdentification().add(identification(id));
            }
            p.setIdentifications(wrapper);
        }
        return p;
    }

    /**
     * Maps the reporting MLRO onto {@code t_person_registration_in_report}. Mapped lossless against the
     * generated type's real slots — including {@code tax_reg_number} and {@code alias} (previously dropped
     * despite having setters). {@code countryOfBirth} has <em>no</em> slot on this XSD type (only
     * {@code birth_place}, a different fact — never fabricated); like every captured-not-filed field it
     * stays preserved verbatim in the persisted {@code report.input} JSON.
     */
    private TPersonRegistrationInReport reportingPerson(DpmsrCreateRequest.Person dto,
                                                        List<ValidationMessage> messages) {
        String path = "reportingPerson";
        TPersonRegistrationInReport p = new TPersonRegistrationInReport();
        p.setGender(dto.gender());
        p.setFirstName(normalizeName(dto.firstName(), path + ".firstName", messages));
        p.setLastName(normalizeName(dto.lastName(), path + ".lastName", messages));
        p.setBirthdate(dto.birthdate());
        p.setNationality1(dto.nationality());
        p.setResidence(dto.residence());
        p.setIdNumber(dto.idNumber());
        p.setTaxRegNumber(dto.taxRegNumber());
        p.setOccupation(dto.occupation());
        p.setAlias(dto.alias());
        if (dto.phone() != null) {
            p.setPhones(GoamlWrappers.wrap(new TPersonRegistrationInReport.Phones(),
                    TPersonRegistrationInReport.Phones::getPhone, phone(dto.phone())));
        }
        if (dto.address() != null) {
            p.setAddresses(GoamlWrappers.wrap(new TPersonRegistrationInReport.Addresses(),
                    TPersonRegistrationInReport.Addresses::getAddress, address(dto.address())));
        }
        return p;
    }

    private TPersonIdentification identification(DpmsrCreateRequest.Identification dto) {
        TPersonIdentification id = new TPersonIdentification();
        id.setType(dto.type());
        id.setNumber(dto.number());
        id.setIssueDate(dto.issueDate());
        id.setExpiryDate(dto.expiryDate());
        id.setIssueCountry(dto.issueCountry());
        return id;
    }

    // ---------- goods ----------

    private TTransItem goods(DpmsrCreateRequest.Goods dto) {
        TTransItem i = new TTransItem();
        i.setItemType(dto.itemType());
        i.setItemMake(dto.itemMake());
        i.setDescription(dto.description());
        i.setPresentlyRegisteredTo(dto.presentlyRegisteredTo());
        i.setStatusCode(dto.statusCode());
        i.setEstimatedValue(dto.estimatedValue());
        i.setCurrencyCode(dto.currencyCode() != null
                ? CurrencyType.fromValue(dto.currencyCode()) : CurrencyType.AED);
        i.setSize(dto.size());
        i.setSizeUom(dto.sizeUom());
        i.setRegistrationDate(dto.registrationDate());
        i.setDisposedValue(dto.disposedValue());
        i.setStatusComments(dto.statusComments());
        i.setRegistrationNumber(dto.registrationNumber());
        i.setIdentificationNumber(dto.identificationNumber());
        return i;
    }

    // ---------- leaves ----------

    private TPhone phone(DpmsrCreateRequest.Phone dto) {
        TPhone p = new TPhone();
        p.setTphContactType(dto.contactType());
        p.setTphCommunicationType(dto.communicationType());
        p.setTphCountryPrefix(dto.countryPrefix());
        p.setTphNumber(dto.number());
        return p;
    }

    private TAddress address(DpmsrCreateRequest.Address dto) {
        if (dto == null) {
            return null;
        }
        TAddress a = new TAddress();
        a.setAddressType(dto.addressType());
        a.setAddress(dto.address());
        a.setCity(dto.city());
        a.setCountryCode(dto.countryCode());
        a.setState(dto.state());
        return a;
    }

    /**
     * Normalize a filed name to the goAML XSD name pattern, surfacing every change: a rewritten name adds a
     * {@code NAME_NORMALIZED} WARNING; a name reduced to nothing adds a {@code NAME_UNREPRESENTABLE} ERROR
     * (which fails validation with a clear message instead of an XSD SAX error).
     */
    private static String normalizeName(String value, String path, List<ValidationMessage> messages) {
        if (value == null) {
            return null;
        }
        String normalized = NameNormalizer.normalize(value);
        if (normalized.isEmpty() && !value.isBlank()) {
            messages.add(ValidationMessage.error(path, NAME_UNREPRESENTABLE,
                    "Name '" + value + "' has no goAML-representable characters after normalization — "
                            + "supply a name using letters, digits, spaces, '.', ''' or '-'"));
        } else if (!normalized.equals(value)) {
            messages.add(ValidationMessage.warning(path, NAME_NORMALIZED,
                    "Name '" + value + "' was normalized to '" + normalized + "' to satisfy the goAML name "
                            + "pattern; the original is preserved in the stored report input"));
        }
        return normalized;
    }
}
