package com.vyttah.goaml.model.mapper.report;

import com.vyttah.goaml.domain.generated.CurrencyType;
import com.vyttah.goaml.domain.generated.EntityPersonRoleType;
import com.vyttah.goaml.domain.generated.ReportPartyType;
import com.vyttah.goaml.domain.generated.TAddress;
import com.vyttah.goaml.domain.generated.TEntity;
import com.vyttah.goaml.domain.generated.TPerson;
import com.vyttah.goaml.domain.generated.TPersonIdentification;
import com.vyttah.goaml.domain.generated.TPersonMyClient;
import com.vyttah.goaml.domain.generated.TPersonRegistrationInReport;
import com.vyttah.goaml.domain.generated.TPhone;
import com.vyttah.goaml.domain.generated.TTransItem;
import com.vyttah.goaml.engine.build.DpmsrReportInput;
import com.vyttah.goaml.engine.build.GoamlParties;
import com.vyttah.goaml.engine.build.GoamlWrappers;
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
                .reportingPerson(reportingPerson(req.reportingPerson()))
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
            return GoamlParties.personMyClient(personMyClient(p.person()), p.reason(), p.comments());
        }
        throw new IllegalArgumentException("party must have an entity or a person");
    }

    private TEntity entity(DpmsrCreateRequest.Entity dto) {
        TEntity e = new TEntity();
        e.setName(dto.name());
        e.setCommercialName(dto.commercialName());
        e.setIncorporationNumber(dto.incorporationNumber());
        e.setIncorporationState(dto.incorporationState());
        e.setIncorporationCountryCode(dto.incorporationCountryCode());
        if (dto.phone() != null) {
            e.setPhones(GoamlWrappers.wrap(new TEntity.Phones(), TEntity.Phones::getPhone, phone(dto.phone())));
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
        d.setFirstName(dto.firstName());
        d.setLastName(dto.lastName());
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

    private TPersonMyClient personMyClient(DpmsrCreateRequest.Person dto) {
        TPersonMyClient p = new TPersonMyClient();
        p.setGender(dto.gender());
        p.setFirstName(dto.firstName());
        p.setLastName(dto.lastName());
        p.setBirthdate(dto.birthdate());
        p.setCountryOfBirth(dto.countryOfBirth());
        p.setNationality1(dto.nationality());
        p.setResidence(dto.residence());
        p.setIdNumber(dto.idNumber());
        p.setTaxRegNumber(dto.taxRegNumber());
        p.setOccupation(dto.occupation());
        // <phones> is a required element on t_person_my_client (its inner <phone> is optional), so a party
        // person always gets a phones wrapper — empty when no phone is supplied — to stay XSD-valid.
        TPersonMyClient.Phones phones = new TPersonMyClient.Phones();
        if (dto.phone() != null) {
            phones.getPhone().add(phone(dto.phone()));
        }
        p.setPhones(phones);
        if (dto.identifications() != null && !dto.identifications().isEmpty()) {
            TPersonMyClient.Identifications wrapper = new TPersonMyClient.Identifications();
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
        p.setFirstName(dto.firstName());
        p.setLastName(dto.lastName());
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
