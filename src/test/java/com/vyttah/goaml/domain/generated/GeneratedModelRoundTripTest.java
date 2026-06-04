package com.vyttah.goaml.domain.generated;

import com.vyttah.goaml.engine.validation.ValidationResult;
import com.vyttah.goaml.engine.validation.XsdSchemaValidator;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.Marshaller;
import jakarta.xml.bind.Unmarshaller;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Step 3 proof: the xjc-generated JAXB model (Step 2) can faithfully <em>read and write</em> the FIU's
 * real DPMSR reports. For each real sample exported from the live UAE goAML portal we:
 * <ol>
 *   <li>unmarshal the real XML into the generated {@link Report},</li>
 *   <li>assert the headline business values survived (who reported, report type, currency, the gold item,
 *       the reporting-entity name) — value fidelity,</li>
 *   <li>re-marshal the model back to XML, and</li>
 *   <li>re-validate that re-marshalled XML against {@code goAMLSchema.xsd} via the Step-1
 *       {@link XsdSchemaValidator} — structural validity, closing the loop with Step 1.</li>
 * </ol>
 *
 * <p>We deliberately assert structural validity + key values, <em>not</em> byte-identical XML: JAXB
 * legitimately reformats whitespace / attribute order, so a byte diff would false-fail. Surviving the
 * XSD gate plus the business-value checks is the honest, robust bar — and the green light for Step 4
 * (re-pointing the engine onto this model).
 *
 * <p>Step 4a note: the generated {@link Report} root exposes <em>typed</em> getters (getRentityId(),
 * getReportCode(), getCurrencyCodeLocal(), getActivity(), …). Earlier (Steps 2–3) it was a single catch-all
 * {@code List<JAXBElement<?>>} because the schema reused the name "activity" across the {@code <xs:choice>}
 * branches; the {@code goaml-bindings.xjb} rename of the transaction-branch's activity to
 * {@code transactionActivity} removed that clash, so xjc regenerated typed properties. This test reads via
 * those typed getters and confirms the rename did not break the round-trip of the real reports.
 */
class GeneratedModelRoundTripTest {

    private final XsdSchemaValidator xsdValidator = new XsdSchemaValidator();

    @ParameterizedTest
    @ValueSource(strings = {
            "samples/TR.2079.200000309.xml",
            "samples/TR.2079.200000310.xml"
    })
    void realSampleRoundTripsThroughGeneratedModel(String resource) throws Exception {
        byte[] originalXml = readResource(resource);

        // (1) unmarshal real XML -> generated Report
        Report report = unmarshal(originalXml);
        assertThat(report).as("unmarshal %s into generated Report", resource).isNotNull();

        // (2) value fidelity — the headline business data read back correctly (via typed getters)
        assertThat(report.getRentityId())
                .as("rentity_id in %s", resource).isEqualTo(3177);
        assertThat(report.getReportCode())
                .as("report_code in %s", resource).isEqualTo(ReportType.DPMSR);
        assertThat(report.getCurrencyCodeLocal())
                .as("currency_code_local in %s", resource).isEqualTo(CurrencyType.AED);

        // The activity-shaped report's <activity> is unmarshalled into the branch-1 property
        // (getReportActivity()); branch-2's getActivity() is the vestigial choice member — see goaml-bindings.xjb.
        ActivityType activity = report.getReportActivity();
        assertThat(activity).as("activity block in %s", resource).isNotNull();

        TTransItem item = activity.getGoodsServices().getItem().get(0);
        assertThat(item.getItemType()).as("gold item type in %s", resource).isEqualTo("GOLD");
        assertThat(item.getCurrencyCode()).as("item currency in %s", resource).isEqualTo(CurrencyType.AED);
        assertThat(item.getSize()).as("item size in %s", resource).isEqualByComparingTo(new BigDecimal("15"));
        assertThat(item.getSizeUom()).as("item size uom in %s", resource).isEqualTo("KG");

        TEntity entity = activity.getReportParties().getReportParty().get(0).getEntity();
        assertThat(entity).as("reporting entity in %s", resource).isNotNull();
        assertThat(entity.getName())
                .as("reporting-entity name in %s", resource).isEqualTo("Example Jewellery LLC");

        // (3) re-marshal generated model -> XML
        byte[] remarshalled = marshal(report);
        assertThat(remarshalled).as("re-marshalled XML for %s", resource).isNotEmpty();

        // (4) re-marshalled XML still conforms to goAMLSchema.xsd (Step-1 gate)
        ValidationResult result = xsdValidator.validate(remarshalled);
        assertThat(result.isValid())
                .as("re-marshalled %s should still conform to goAMLSchema.xsd; errors=%s",
                        resource, result.errors())
                .isTrue();
    }

    // --- helpers -----------------------------------------------------------------------------------

    private static Report unmarshal(byte[] xml) throws Exception {
        JAXBContext ctx = JAXBContext.newInstance(ObjectFactory.class);
        Unmarshaller unmarshaller = ctx.createUnmarshaller();
        return (Report) unmarshaller.unmarshal(new ByteArrayInputStream(xml));
    }

    private static byte[] marshal(Report report) throws Exception {
        JAXBContext ctx = JAXBContext.newInstance(ObjectFactory.class);
        Marshaller marshaller = ctx.createMarshaller();
        marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.TRUE);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        marshaller.marshal(report, out); // Report is @XmlRootElement(name="report") — no ObjectFactory wrap needed
        return out.toByteArray();
    }

    private static byte[] readResource(String path) throws IOException {
        try (InputStream in = Thread.currentThread().getContextClassLoader().getResourceAsStream(path)) {
            assertThat(in).as("test resource %s present on classpath", path).isNotNull();
            return in.readAllBytes();
        }
    }
}
