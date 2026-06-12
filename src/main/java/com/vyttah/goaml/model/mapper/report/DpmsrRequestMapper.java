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
import com.vyttah.goaml.model.dto.report.DpmsrCreateRequest;
import org.springframework.stereotype.Component;

/**
 * Maps the curated {@link DpmsrCreateRequest} JSON contract onto the engine's {@link DpmsrReportInput}
 * (generated JAXB leaf types). Hand-written rather than MapStruct because the target is the goAML
 * wrapper-per-owner JAXB model (each owner has its own {@code Phones}/{@code Addresses}/… class).
 * Only set-when-present, so optional fields don't emit empty elements.
 */
@Component
public class DpmsrRequestMapper {

    public DpmsrReportInput toInput(DpmsrCreateRequest req, int rentityId) {
        DpmsrReportInput.Builder b = DpmsrReportInput.builder()
                .rentityId(rentityId)
                .rentityBranch(req.rentityBranch())
                .entityReference(req.entityReference())
                .submissionDate(req.submissionDate())
                .fiuRefNumber(req.fiuRefNumber())
                .reportingPerson(req.reportingPerson() != null ? reportingPerson(req.reportingPerson()) : null)
                .location(address(req.location()))
                .reason(req.reason())
                .action(req.action());

        if (req.indicators() != null) {
            b.indicators(req.indicators().toArray(String[]::new));
        }
        if (req.parties() != null) {
            for (DpmsrCreateRequest.Party p : req.parties()) {
                b.party(party(p));
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

    private ReportPartyType party(DpmsrCreateRequest.Party p) {
        if (p.entity() != null) {
            return GoamlParties.entity(entity(p.entity()), p.reason(), p.comments());
        }
        if (p.person() != null) {
            return GoamlParties.person(person(p.person()), p.reason(), p.comments());
        }
        throw new IllegalArgumentException("party must have an entity or a person");
    }

    private TEntity entity(DpmsrCreateRequest.Entity dto) {
        TEntity e = new TEntity();
        // C8: normalize XSD-illegal punctuation (&, (), comma, /) in name fields so a real UAE legal name
        // stays XSD-valid instead of failing marshalling with a raw SAX pattern error.
        e.setName(NameNormalizer.normalize(dto.name()));
        e.setCommercialName(NameNormalizer.normalize(dto.commercialName()));
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
            for (DpmsrCreateRequest.Director d : dto.directors()) {
                e.getDirectorId().add(director(d));
            }
        }
        return e;
    }

    private TEntity.DirectorId director(DpmsrCreateRequest.Director dto) {
        TEntity.DirectorId d = new TEntity.DirectorId();
        d.setGender(dto.gender());
        d.setFirstName(NameNormalizer.normalize(dto.firstName()));
        d.setLastName(NameNormalizer.normalize(dto.lastName()));
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
    private TPerson person(DpmsrCreateRequest.Person dto) {
        TPerson p = new TPerson();
        p.setGender(dto.gender());
        p.setFirstName(NameNormalizer.normalize(dto.firstName()));
        p.setLastName(NameNormalizer.normalize(dto.lastName()));
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

    private TPersonRegistrationInReport reportingPerson(DpmsrCreateRequest.Person dto) {
        TPersonRegistrationInReport p = new TPersonRegistrationInReport();
        p.setGender(dto.gender());
        p.setFirstName(NameNormalizer.normalize(dto.firstName()));
        p.setLastName(NameNormalizer.normalize(dto.lastName()));
        p.setBirthdate(dto.birthdate());
        p.setNationality1(dto.nationality());
        p.setResidence(dto.residence());
        p.setIdNumber(dto.idNumber());
        p.setOccupation(dto.occupation());
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
}
