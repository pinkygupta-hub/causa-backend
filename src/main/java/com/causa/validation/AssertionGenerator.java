package com.causa.validation;

/**
 * Contract for Phase 1 RCA assertion generation.
 *
 * <p>Designed so a deterministic implementation can be replaced later by an
 * LLM-backed implementation without changing callers.
 *
 * @since 0.0.1
 */
public interface AssertionGenerator {

    /**
     * Decomposes an RCA statement into independently testable assertions.
     *
     * @param request assertion generation input
     * @return generated assertions and rendered prompt
     */
    AssertionGenerationResult generateAssertions(AssertionGenerationRequest request);
}

// Made with Bob
