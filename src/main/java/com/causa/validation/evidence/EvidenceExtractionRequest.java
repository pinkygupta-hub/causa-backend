package com.causa.validation.evidence;

import java.util.Objects;

import com.causa.validation.ValidationAssertion;
import com.causa.validation.shared.MockValidationContext;

/**
 * Phase 2 request model for extracting evidence for a single assertion.
 *
 * @param assertion single assertion to validate
 * @param validationContext available logs, events, and metrics
 * @since 0.0.1
 */
public record EvidenceExtractionRequest(
    ValidationAssertion assertion,
    MockValidationContext validationContext
) {

    public EvidenceExtractionRequest {
        Objects.requireNonNull(assertion, "assertion cannot be null");
        Objects.requireNonNull(validationContext, "validationContext cannot be null");
    }
}

// Made with Bob
