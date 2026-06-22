package com.causa.validation;

import java.util.List;
import java.util.Objects;

/**
 * Phase 1 result model containing generated assertions and the prompt used.
 *
 * @param rcaStatement original RCA statement
 * @param promptTemplate rendered prompt template for future LLM integration
 * @param assertions generated independent assertions
 * @since 0.0.1
 */
public record AssertionGenerationResult(
    String rcaStatement,
    String promptTemplate,
    List<ValidationAssertion> assertions
) {

    public AssertionGenerationResult {
        Objects.requireNonNull(rcaStatement, "rcaStatement cannot be null");
        Objects.requireNonNull(promptTemplate, "promptTemplate cannot be null");
        assertions = assertions == null ? List.of() : List.copyOf(assertions);
    }
}

// Made with Bob
