package com.causa.validation.evaluation;

import java.util.Objects;

import com.causa.validation.ValidationAssertion;
import com.causa.validation.evidence.EvidenceExtractionResult;

/**
 * Phase 3 request model for validating a single assertion with its evidence.
 *
 * @param assertion original assertion to validate
 * @param evidenceResult extracted supporting and contradicting evidence
 * @since 0.0.1
 */
public record AssertionValidationRequest(
    ValidationAssertion assertion,
    EvidenceExtractionResult evidenceResult
) {

    public AssertionValidationRequest {
        Objects.requireNonNull(assertion, "assertion cannot be null");
        Objects.requireNonNull(evidenceResult, "evidenceResult cannot be null");
    }
}

// Made with Bob
