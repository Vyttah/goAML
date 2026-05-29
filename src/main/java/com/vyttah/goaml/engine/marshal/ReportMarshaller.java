package com.vyttah.goaml.engine.marshal;

import com.vyttah.goaml.domain.Report;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Marshaller;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

/**
 * JAXB wrapper that marshals a {@link Report} POJO tree to UTF-8 XML bytes (and back).
 * The {@link JAXBContext} is built once at construction and reused — context creation
 * is expensive and the context is thread-safe.
 */
@Component
public class ReportMarshaller {

    private static final String REPORT_XML_DECLARATION =
            "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>";

    private final JAXBContext jaxbContext;

    public ReportMarshaller() {
        try {
            this.jaxbContext = JAXBContext.newInstance(Report.class);
        } catch (JAXBException e) {
            throw new IllegalStateException("Failed to build JAXBContext for Report", e);
        }
    }

    public byte[] marshal(Report report) {
        try {
            Marshaller m = jaxbContext.createMarshaller();
            m.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.TRUE);
            m.setProperty(Marshaller.JAXB_ENCODING, "UTF-8");
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            m.marshal(report, baos);
            return baos.toByteArray();
        } catch (JAXBException e) {
            throw new MarshallingException("Failed to marshal Report", e);
        }
    }

    public Report unmarshal(byte[] xml) {
        try {
            return (Report) jaxbContext.createUnmarshaller()
                    .unmarshal(new ByteArrayInputStream(xml));
        } catch (JAXBException e) {
            throw new MarshallingException("Failed to unmarshal Report", e);
        }
    }

    /** For tests / debugging — exposes the XML declaration the marshaller emits. */
    public static String xmlDeclaration() {
        return REPORT_XML_DECLARATION;
    }
}
