package com.vyttah.goaml.b2b;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class B2bConfigAndPropertiesTest {

    @Test
    void propertiesDefaultTtlWhenNull() {
        assertThat(new B2bProperties(null).tokenTtl()).isEqualTo(Duration.ofMinutes(20));
    }

    @Test
    void propertiesKeepExplicitTtl() {
        assertThat(new B2bProperties(Duration.ofMinutes(5)).tokenTtl()).isEqualTo(Duration.ofMinutes(5));
    }

    @Test
    void configProvidesHttp11RequestFactory() {
        assertThat(new B2bConfig().b2bRequestFactory()).isNotNull();
    }
}
