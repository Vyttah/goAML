package com.vyttah.goaml.domain.generated;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.DeserializationConfig;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializationConfig;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.deser.Deserializers;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.Serializers;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.vyttah.goaml.engine.marshal.ReportMarshaller;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Step 0 spike for the full-schema-fidelity plan: prove the xjc-generated domain types can serve as the
 * <em>JSON contract</em> directly (so we expose the full schema 1:1 instead of hand-maintaining a curated
 * DTO). Round-trips the real sample's richest objects — {@link TTransItem} (enum + {@code BigDecimal} +
 * scalars) and {@link TEntity} (nested director, enum {@code role}, wrapper lists, {@code OffsetDateTime})
 * — through Jackson and back, asserting fidelity.
 *
 * <p>The one wrinkle the plan predicted: xjc enums bind by Java <em>constant name</em> under default Jackson,
 * but the schema value can differ from the name (e.g. {@code "-"} placeholders, digit-leading codes). The
 * reflective {@code XmlEnumModule} below binds them via the generated {@code value()}/{@code fromValue(String)}
 * accessors — this is the prototype that graduates to {@code main} in Step 1.
 */
class GeneratedTypeJsonBindingSpikeTest {

    private final ReportMarshaller marshaller = new ReportMarshaller();

    private final JsonMapper mapper = JsonMapper.builder()
            .addModule(new JavaTimeModule())
            .addModule(new XmlEnumModule())
            .serializationInclusion(JsonInclude.Include.NON_NULL)
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .build();

    @Test
    void goodsItemRoundTripsThroughJsonWithAllGapFields() throws IOException {
        TTransItem item = sample().getReportActivity().getGoodsServices().getItem().get(0);

        TTransItem back = mapper.readValue(mapper.writeValueAsString(item), TTransItem.class);

        assertThat(back.getItemType()).isEqualTo("GOLD");
        assertThat(back.getCurrencyCode()).as("enum via value()/fromValue()").isEqualTo(CurrencyType.AED);
        assertThat(back.getEstimatedValue()).isEqualByComparingTo(new BigDecimal("10000000.00"));
        assertThat(back.getDisposedValue()).isEqualByComparingTo(new BigDecimal("10050000.00"));
        assertThat(back.getStatusComments()).contains("CASH RECEIVED");
        assertThat(back.getRegistrationNumber()).isEqualTo("SAMPLE0000001");
        assertThat(back.getIdentificationNumber()).isEqualTo("REC0000000001");
    }

    @Test
    void entityWithNestedDirectorRoundTripsThroughJson() throws IOException {
        TEntity entity = sample().getReportActivity().getReportParties().getReportParty().get(0).getEntity();

        TEntity back = mapper.readValue(mapper.writeValueAsString(entity), TEntity.class);

        assertThat(back.getName()).isEqualTo("SAMPLE JEWELLERY L.L.C");
        assertThat(back.getIncorporationDate()).as("OffsetDateTime round-trip").isNotNull();
        assertThat(back.getAddresses().getAddress().get(0).getAddress()).contains("SAMPLE BUILDING");

        TEntity.DirectorId director = back.getDirectorId().get(0);
        assertThat(director.getRole()).as("enum role via module").isEqualTo(EntityPersonRoleType.PRTNR);
        assertThat(director.getSsn()).isEqualTo("784198000000001");
        assertThat(director.getEmployerAddressId()).isNotNull();
        assertThat(director.getEmployerPhoneId()).isNotNull();
        assertThat(director.getIdentification().get(0).getType()).isEqualTo("EID");
        assertThat(director.getAddresses().getAddress().get(0).getAddress()).isEqualTo("AL RAS DUBAI");
    }

    private Report sample() throws IOException {
        try (InputStream in = Thread.currentThread().getContextClassLoader()
                .getResourceAsStream("samples/USG-dpmsr-activity.xml")) {
            assertThat(in).as("sample present on classpath").isNotNull();
            return marshaller.unmarshal(in.readAllBytes());
        }
    }

    // --- reflective Jackson binding for xjc @XmlEnum types (value()/fromValue(String)) -----------------

    /** Binds any enum exposing {@code String value()} + {@code static X fromValue(String)} by its schema value. */
    static final class XmlEnumModule extends SimpleModule {
        @Override
        public void setupModule(SetupContext context) {
            context.addSerializers(new Serializers.Base() {
                @Override
                public JsonSerializer<?> findSerializer(SerializationConfig cfg, com.fasterxml.jackson.databind.JavaType type,
                                                        BeanDescription desc) {
                    Class<?> raw = type.getRawClass();
                    return raw.isEnum() && method(raw, "value") != null ? new ValueSerializer() : null;
                }
            });
            context.addDeserializers(new Deserializers.Base() {
                @Override
                public JsonDeserializer<?> findEnumDeserializer(Class<?> type, DeserializationConfig cfg,
                                                                BeanDescription desc) {
                    return method(type, "fromValue", String.class) != null ? new FromValueDeserializer(type) : null;
                }
            });
        }
    }

    static final class ValueSerializer extends JsonSerializer<Object> {
        @Override
        public void serialize(Object value, JsonGenerator gen, SerializerProvider sp) throws IOException {
            try {
                gen.writeString((String) value.getClass().getMethod("value").invoke(value));
            } catch (ReflectiveOperationException e) {
                throw new IOException("xjc enum value() failed for " + value.getClass(), e);
            }
        }
    }

    static final class FromValueDeserializer extends JsonDeserializer<Object> {
        private final Class<?> type;

        FromValueDeserializer(Class<?> type) {
            this.type = type;
        }

        @Override
        public Object deserialize(JsonParser p, DeserializationContext ctx) throws IOException {
            try {
                return type.getMethod("fromValue", String.class).invoke(null, p.getValueAsString());
            } catch (ReflectiveOperationException e) {
                throw new IOException("xjc enum fromValue() failed for " + type, e);
            }
        }
    }

    private static Method method(Class<?> type, String name, Class<?>... params) {
        try {
            return type.getMethod(name, params);
        } catch (NoSuchMethodException e) {
            return null;
        }
    }
}
