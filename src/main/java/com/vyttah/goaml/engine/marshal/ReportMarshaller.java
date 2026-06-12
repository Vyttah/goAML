package com.vyttah.goaml.engine.marshal;

import com.vyttah.goaml.domain.generated.ObjectFactory;
import com.vyttah.goaml.domain.generated.Report;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Marshaller;
import org.springframework.stereotype.Component;
import org.xml.sax.InputSource;
import org.xml.sax.XMLReader;

import javax.xml.parsers.SAXParserFactory;
import javax.xml.transform.sax.SAXSource;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

/**
 * JAXB wrapper that marshals a {@link Report} POJO tree to UTF-8 XML bytes (and back).
 * The {@link JAXBContext} is built once at construction and reused — context creation
 * is expensive and the context is thread-safe.
 *
 * <p>{@link #unmarshal(byte[])} parses through a hardened SAX reader (DOCTYPE declarations and external
 * general/parameter entities disabled — mirroring {@code XsdSchemaValidator}'s XXE hardening), because the
 * bytes can be caller-supplied (the goAML XML import) and a plain JAXB unmarshaller would otherwise resolve
 * DTDs/entities <em>before</em> the XSD validation gate ever runs.
 */
@Component
public class ReportMarshaller {

    private static final String REPORT_XML_DECLARATION =
            "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>";

    private final JAXBContext jaxbContext;

    public ReportMarshaller() {
        try {
            this.jaxbContext = JAXBContext.newInstance(ObjectFactory.class);
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
            SAXSource source = new SAXSource(hardenedXmlReader(),
                    new InputSource(new ByteArrayInputStream(xml)));
            return (Report) jaxbContext.createUnmarshaller().unmarshal(source);
        } catch (JAXBException e) {
            throw new MarshallingException("Failed to unmarshal Report", e);
        }
    }

    /** An {@link XMLReader} with DOCTYPE + external entity resolution disabled (XXE hardening). */
    private static XMLReader hardenedXmlReader() {
        try {
            SAXParserFactory factory = SAXParserFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setXIncludeAware(false);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            return factory.newSAXParser().getXMLReader();
        } catch (Exception e) {
            throw new MarshallingException("Failed to create a hardened XML reader", e);
        }
    }

    /** For tests / debugging — exposes the XML declaration the marshaller emits. */
    public static String xmlDeclaration() {
        return REPORT_XML_DECLARATION;
    }
}
