package com.vyttah.goaml.domain.jackson;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.DeserializationConfig;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializationConfig;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.deser.Deserializers;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.Serializers;

import java.io.IOException;
import java.lang.reflect.Method;

/**
 * Jackson module that binds the xjc-generated goAML enums by their <em>schema value</em> rather than their
 * Java constant name. Each generated {@code @XmlEnum} exposes {@code String value()} and
 * {@code static X fromValue(String)} (the constant name is mangled for codes like {@code "-"} or
 * digit-leading values, so default name-based binding is wrong). This lets the generated domain types serve
 * directly as the JSON contract for the full-schema-fidelity DPMSR API.
 *
 * <p>Scoped to {@code com.vyttah.goaml.domain.generated} so it never touches the application's own enums
 * (which bind by name as usual). Registered as a Spring bean (see {@code JacksonConfig}); Spring Boot adds
 * any {@link com.fasterxml.jackson.databind.Module} bean to the auto-configured {@code ObjectMapper}.
 */
public class GeneratedEnumJacksonModule extends SimpleModule {

    private static final String GENERATED_PACKAGE = "com.vyttah.goaml.domain.generated";

    public GeneratedEnumJacksonModule() {
        super("GoamlGeneratedEnumModule");
    }

    @Override
    public void setupModule(SetupContext context) {
        super.setupModule(context);
        context.addSerializers(new Serializers.Base() {
            @Override
            public JsonSerializer<?> findSerializer(SerializationConfig config, JavaType type, BeanDescription desc) {
                Class<?> raw = type.getRawClass();
                return isGeneratedEnum(raw) && method(raw, "value") != null ? new ValueSerializer() : null;
            }
        });
        context.addDeserializers(new Deserializers.Base() {
            @Override
            public JsonDeserializer<?> findEnumDeserializer(Class<?> type, DeserializationConfig config,
                                                            BeanDescription desc) {
                return isGeneratedEnum(type) && method(type, "fromValue", String.class) != null
                        ? new FromValueDeserializer(type) : null;
            }
        });
    }

    private static boolean isGeneratedEnum(Class<?> raw) {
        return raw.isEnum() && raw.getName().startsWith(GENERATED_PACKAGE);
    }

    private static Method method(Class<?> type, String name, Class<?>... params) {
        try {
            return type.getMethod(name, params);
        } catch (NoSuchMethodException e) {
            return null;
        }
    }

    /** Serializes a generated enum as its {@code value()} (the schema string). */
    static final class ValueSerializer extends JsonSerializer<Object> {
        @Override
        public void serialize(Object value, JsonGenerator gen, SerializerProvider sp) throws IOException {
            try {
                gen.writeString((String) value.getClass().getMethod("value").invoke(value));
            } catch (ReflectiveOperationException e) {
                throw new IOException("Generated enum value() failed for " + value.getClass(), e);
            }
        }
    }

    /** Deserializes a generated enum from its schema string via {@code fromValue(String)}. */
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
                throw new IOException("Generated enum fromValue() failed for " + type, e);
            }
        }
    }
}
