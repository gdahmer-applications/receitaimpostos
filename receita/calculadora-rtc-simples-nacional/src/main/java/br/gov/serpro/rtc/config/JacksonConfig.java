package br.gov.serpro.rtc.config;

import java.time.LocalDate;
import java.time.OffsetDateTime;

import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import br.gov.serpro.rtc.shared.serializer.LocalDateCustomDeserializer;
import br.gov.serpro.rtc.shared.serializer.OffsetDateTimeCustomDeserializer;

/**
 * Configura a personalização do Jackson, registrando deserializadores de datas
 * usados pela API.
 */
@Configuration
public class JacksonConfig {

    @Bean
    Jackson2ObjectMapperBuilderCustomizer customizer(LocalDateCustomDeserializer localDateDeserializer,
            OffsetDateTimeCustomDeserializer offsetDateTimeDeserializer) {
        return builder -> builder
                .deserializerByType(LocalDate.class, localDateDeserializer)
                .deserializerByType(OffsetDateTime.class, offsetDateTimeDeserializer);
    }

}
