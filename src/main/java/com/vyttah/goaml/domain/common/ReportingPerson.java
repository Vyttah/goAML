package com.vyttah.goaml.domain.common;

import com.vyttah.goaml.domain.adapter.GoamlDateTimeAdapter;
import com.vyttah.goaml.domain.party.TAddress;
import com.vyttah.goaml.domain.party.TPersonIdentification;
import com.vyttah.goaml.domain.party.TPhone;
import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlElementWrapper;
import jakarta.xml.bind.annotation.XmlType;
import jakarta.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * goAML {@code t_person_registration_in_report} — the MLRO / reporting officer whose details
 * sit directly under the {@code <reporting_person>} element of a {@code <report>}. Shares the
 * person shape used elsewhere; modeled as its own class so JAXB emits the schema's typed name.
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "t_person_registration_in_report", propOrder = {
        "gender", "title", "firstName", "middleName", "prefix", "lastName",
        "birthdate", "birthPlace", "mothersName", "alias",
        "ssn", "passportNumber", "passportCountry", "idNumber",
        "phones", "addresses",
        "nationality1", "nationality2", "nationality3", "residence",
        "email", "occupation", "employerName",
        "identifications",
        "comments"
})
public class ReportingPerson {

    @XmlElement(name = "gender") private String gender;
    @XmlElement(name = "title") private String title;
    @XmlElement(name = "first_name") private String firstName;
    @XmlElement(name = "middle_name") private String middleName;
    @XmlElement(name = "prefix") private String prefix;
    @XmlElement(name = "last_name") private String lastName;

    @XmlElement(name = "birthdate") @XmlJavaTypeAdapter(GoamlDateTimeAdapter.class)
    private OffsetDateTime birthdate;
    @XmlElement(name = "birth_place") private String birthPlace;
    @XmlElement(name = "mothers_name") private String mothersName;
    @XmlElement(name = "alias") private String alias;

    @XmlElement(name = "ssn") private String ssn;
    @XmlElement(name = "passport_number") private String passportNumber;
    @XmlElement(name = "passport_country") private String passportCountry;
    @XmlElement(name = "id_number") private String idNumber;

    @XmlElementWrapper(name = "phones")
    @XmlElement(name = "phone")
    private List<TPhone> phones = new ArrayList<>();

    @XmlElementWrapper(name = "addresses")
    @XmlElement(name = "address")
    private List<TAddress> addresses = new ArrayList<>();

    @XmlElement(name = "nationality1") private String nationality1;
    @XmlElement(name = "nationality2") private String nationality2;
    @XmlElement(name = "nationality3") private String nationality3;
    @XmlElement(name = "residence") private String residence;

    @XmlElement(name = "email") private String email;
    @XmlElement(name = "occupation") private String occupation;
    @XmlElement(name = "employer_name") private String employerName;

    @XmlElement(name = "identification")
    private List<TPersonIdentification> identifications = new ArrayList<>();

    @XmlElement(name = "comments") private String comments;

    public String getGender() { return gender; }
    public void setGender(String v) { this.gender = v; }
    public String getTitle() { return title; }
    public void setTitle(String v) { this.title = v; }
    public String getFirstName() { return firstName; }
    public void setFirstName(String v) { this.firstName = v; }
    public String getMiddleName() { return middleName; }
    public void setMiddleName(String v) { this.middleName = v; }
    public String getPrefix() { return prefix; }
    public void setPrefix(String v) { this.prefix = v; }
    public String getLastName() { return lastName; }
    public void setLastName(String v) { this.lastName = v; }
    public OffsetDateTime getBirthdate() { return birthdate; }
    public void setBirthdate(OffsetDateTime v) { this.birthdate = v; }
    public String getBirthPlace() { return birthPlace; }
    public void setBirthPlace(String v) { this.birthPlace = v; }
    public String getMothersName() { return mothersName; }
    public void setMothersName(String v) { this.mothersName = v; }
    public String getAlias() { return alias; }
    public void setAlias(String v) { this.alias = v; }
    public String getSsn() { return ssn; }
    public void setSsn(String v) { this.ssn = v; }
    public String getPassportNumber() { return passportNumber; }
    public void setPassportNumber(String v) { this.passportNumber = v; }
    public String getPassportCountry() { return passportCountry; }
    public void setPassportCountry(String v) { this.passportCountry = v; }
    public String getIdNumber() { return idNumber; }
    public void setIdNumber(String v) { this.idNumber = v; }
    public List<TPhone> getPhones() { return phones; }
    public void setPhones(List<TPhone> v) { this.phones = v == null ? new ArrayList<>() : new ArrayList<>(v); }
    public List<TAddress> getAddresses() { return addresses; }
    public void setAddresses(List<TAddress> v) { this.addresses = v == null ? new ArrayList<>() : new ArrayList<>(v); }
    public String getNationality1() { return nationality1; }
    public void setNationality1(String v) { this.nationality1 = v; }
    public String getNationality2() { return nationality2; }
    public void setNationality2(String v) { this.nationality2 = v; }
    public String getNationality3() { return nationality3; }
    public void setNationality3(String v) { this.nationality3 = v; }
    public String getResidence() { return residence; }
    public void setResidence(String v) { this.residence = v; }
    public String getEmail() { return email; }
    public void setEmail(String v) { this.email = v; }
    public String getOccupation() { return occupation; }
    public void setOccupation(String v) { this.occupation = v; }
    public String getEmployerName() { return employerName; }
    public void setEmployerName(String v) { this.employerName = v; }
    public List<TPersonIdentification> getIdentifications() { return identifications; }
    public void setIdentifications(List<TPersonIdentification> v) {
        this.identifications = v == null ? new ArrayList<>() : new ArrayList<>(v);
    }
    public String getComments() { return comments; }
    public void setComments(String v) { this.comments = v; }
}
