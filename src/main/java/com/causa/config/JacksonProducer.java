package com.causa.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

/**
 * CDI producer for Jackson ObjectMapper.
 *
 * @since 0.0.1
 */
@ApplicationScoped
public class JacksonProducer {

    @Produces
    @ApplicationScoped
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }
}
