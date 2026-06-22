package com.causa.validation.evaluation;

import java.util.Objects;

import com.causa.validation.shared.AssertionValidationStatus;

/**
 * Phase 3 result model containing validation outcome for a single assertion.
 *
 * @param assertionClaim original assertion claim text
 * @param promptTemplate rendered prompt template for future LLM integration
 * @param status validation status (SUPPORTED/WEAKLY_SUPPORTED/UNSUPPORTED)
 * @param confidence confidence score (0-100)
 * @param reasoning detailed reasoning for the validation decision
 * @since 0.0.1
 */
public record AssertionValidationResult(
    String assertionClaim,
    String promptTemplate,
    AssertionValidationStatus status,
    int confidence,
    String reasoning
) {

    public AssertionValidationResult {
        Objects.requireNonNull(assertionClaim, "assertionClaim cannot be null");
        Objects.requireNonNull(promptTemplate, "promptTemplate cannot be null");
        Objects.requireNonNull(status, "status cannot be null");
        Objects.requireNonNull(reasoning, "reasoning cannot be null");
        
        if (confidence < 0 || confidence > 100) {
            throw new IllegalArgumentException("confidence must be between 0 and 100, got: " + confidence);
        }
    }
}

// Made with Bob
