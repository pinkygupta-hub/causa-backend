package com.causa.validation.evaluation;

/**
 * Contract for Phase 3 independent assertion validation.
 *
 * <p>Designed so a deterministic implementation can be replaced later by an
 * LLM-backed implementation without changing callers.
 *
 * @since 0.0.1
 */
public interface AssertionValidator {

    /**
     * Validates a single assertion using only the provided evidence.
     *
     * @param request assertion validation input
     * @return validation result with status, confidence, and reasoning
     */
    AssertionValidationResult validate(AssertionValidationRequest request);
}

// Made with Bob
