package com.vyttah.goaml.engine.validation;

import org.springframework.stereotype.Component;
import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

import javax.xml.XMLConstants;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;

/**
 * Validates a marshalled goAML report against the authoritative goAML XSD
 * (schema v5.0.2, classpath {@code xsd/goaml/5.0.2/goAMLSchema.xsd}).
 *
 * <p>The schema is self-contained (no {@code xs:include}/{@code xs:import}) and carries no XSD 1.1
 * {@code <xs:assert>} rules, so the standard JDK JAXP validator (XSD 1.0) validates it fully — no
 * Saxon / Xerces-EE needed. The {@link Schema} is compiled once at construction (expensive, and
 * thread-safe) and reused; a {@link Validator} is created per call (a {@code Validator} is not
 * thread-safe).
 *
 * <p>Findings are <em>collected</em> (not thrown) into a {@link ValidationResult} using the shared
 * validation model, so an XSD failure carries the same shape as a business-rule failure
 * ({@link ReportValidator}). External entity / DTD resolution is disabled (XXE hardening).
 */
@Component
public class XsdSchemaValidator {

    /** Classpath location of the authoritative goAML report schema. */
    public static final String SCHEMA_RESOURCE = "xsd/goaml/5.0.2/goAMLSchema.xsd";

    /** Machine code stamped on XSD findings (SAX reports line/column, not a dotted path). */
    private static final String XSD_CODE = "XSD";
    private static final String XSD_PATH = "report";

    private final Schema schema;

    public XsdSchemaValidator() {
        this(SCHEMA_RESOURCE);
    }

    XsdSchemaValidator(String schemaResource) {
        this.schema = compile(schemaResource);
    }

    private static Schema compile(String schemaResource) {
        URL url = Thread.currentThread().getContextClassLoader().getResource(schemaResource);
        if (url == null) {
            url = XsdSchemaValidator.class.getClassLoader().getResource(schemaResource);
        }
        if (url == null) {
            throw new IllegalStateException("goAML XSD not found on classpath: " + schemaResource);
        }
        SchemaFactory factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
        disableExternalAccess(factory::setProperty);
        try {
            return factory.newSchema(url);
        } catch (SAXException e) {
            throw new IllegalStateException("Failed to compile goAML XSD: " + schemaResource, e);
        }
    }

    /**
     * Validate a marshalled report against the goAML XSD, collecting every violation.
     *
     * @param reportXml the report XML bytes
     * @return a {@link ValidationResult}; {@link ValidationResult#isValid()} is true when the XML conforms
     */
    public ValidationResult validate(byte[] reportXml) {
        ValidationResult result = new ValidationResult();
        Validator validator = schema.newValidator();
        disableExternalAccess(validator::setProperty);
        validator.setErrorHandler(new CollectingHandler(result));
        try (InputStream in = new ByteArrayInputStream(reportXml)) {
            validator.validate(new StreamSource(in));
        } catch (SAXException e) {
            // A parse failure not surfaced via the handler (e.g. not well-formed XML).
            result.error(XSD_PATH, XSD_CODE, e.getMessage());
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read report XML for validation", e);
        }
        return result;
    }

    /** Best-effort XXE hardening: forbid external DTD/schema resolution where supported. */
    private static void disableExternalAccess(PropertySetter setter) {
        try {
            setter.set(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            setter.set(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        } catch (SAXException ignored) {
            // Property unsupported on this implementation — acceptable; the schema is self-contained.
        }
    }

    @FunctionalInterface
    private interface PropertySetter {
        void set(String name, Object value) throws SAXException;
    }

    /** Collects SAX validation events into the shared {@link ValidationResult} instead of throwing. */
    private static final class CollectingHandler implements ErrorHandler {
        private final ValidationResult result;

        CollectingHandler(ValidationResult result) {
            this.result = result;
        }

        @Override
        public void warning(SAXParseException e) {
            result.warning(XSD_PATH, XSD_CODE, format(e));
        }

        @Override
        public void error(SAXParseException e) {
            result.error(XSD_PATH, XSD_CODE, format(e));
        }

        @Override
        public void fatalError(SAXParseException e) {
            result.error(XSD_PATH, XSD_CODE, format(e));
        }

        private static String format(SAXParseException e) {
            return "line " + e.getLineNumber() + ", col " + e.getColumnNumber() + ": " + e.getMessage();
        }
    }
}
