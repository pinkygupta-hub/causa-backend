package com.causa.validation;

import java.util.Objects;

/**
 * Phase 1 request model for assertion generation from an RCA statement.
 *
 * <p>Phase 1 decomposes the RCA into testable assertions based solely on the claims made.
 * Evidence extraction (Phase 2) will search logs for proof.
 *
 * @param rcaStatement full RCA statement to decompose into independent assertions
 * @since 0.0.1
 */
public record AssertionGenerationRequest(
    String rcaStatement
) {

    public AssertionGenerationRequest {
        Objects.requireNonNull(rcaStatement, "rcaStatement cannot be null");
    }
}
