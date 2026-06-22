package com.causa.validation;

/**
 * Renders the Phase 1 prompt template for assertion generation.
 *
 * @since 0.0.1
 */
public final class AssertionGenerationPromptTemplate {

    private AssertionGenerationPromptTemplate() {
        throw new AssertionError("Utility class cannot be instantiated");
    }

    /**
     * Renders the prompt template using the provided request.
     *
     * @param request assertion generation input
     * @return rendered prompt text
     */
    public static String render(AssertionGenerationRequest request) {
        return """
            Decompose this RCA into 2-5 independent assertions that can be validated separately:

            RCA: %s

            For each assertion provide:
            1. The claim itself (what the RCA states happened)
            2. A targeted validation question (how to verify this claim)
            3. What evidence would SUPPORT it (what logs/metrics would prove it)
            4. What evidence would CONTRADICT it (what would disprove it)
            5. Keywords to search logs/metrics for (search terms)

            Focus on the CLAIMS made in the RCA, not on what evidence exists.
            Evidence extraction will happen in a separate phase.

            Return as structured JSON array.
            """.formatted(
            request.rcaStatement()
        );
    }
}

// Made with Bob
