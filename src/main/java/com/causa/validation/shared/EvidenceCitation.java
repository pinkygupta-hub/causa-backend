package com.causa.validation.shared;

import java.util.Objects;

/**
 * Exact evidence citation extracted from a validation data source.
 *
 * @param sourceType source category for the citation
 * @param sourceName source identifier such as file, stream, or subsystem
 * @param timestamp timestamp associated with the evidence line
 * @param excerpt exact cited content
 * @since 0.0.1
 */
public record EvidenceCitation(
    ValidationDataSourceType sourceType,
    String sourceName,
    String timestamp,
    String excerpt
) {

    public EvidenceCitation {
        Objects.requireNonNull(sourceType, "sourceType cannot be null");
        Objects.requireNonNull(sourceName, "sourceName cannot be null");
        Objects.requireNonNull(timestamp, "timestamp cannot be null");
        Objects.requireNonNull(excerpt, "excerpt cannot be null");
    }
}

// Made with Bob
