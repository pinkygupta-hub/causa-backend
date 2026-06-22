package com.causa.validation.evidence;

import com.causa.validation.shared.MockValidationContext;

/**
 * Renders the Phase 2 prompt template for evidence extraction.
 *
 * @since 0.0.1
 */
public final class EvidenceExtractionPromptTemplate {

    private EvidenceExtractionPromptTemplate() {
        throw new AssertionError("Utility class cannot be instantiated");
    }

    /**
     * Renders the prompt template using the provided request.
     *
     * @param request evidence extraction input
     * @return rendered prompt text
     */
    public static String render(EvidenceExtractionRequest request) {
        MockValidationContext context = request.validationContext();

        return """
            Extract EXACT evidence for this specific assertion:

            Assertion: %s
            Question: %s

            Available Data:
            --- POD LOGS ---
            %s

            --- KUBERNETES EVENTS ---
            %s

            --- RESOURCE USAGE ---
            %s

            --- KRUIZE RECOMMENDATIONS ---
            %s

            Task:
            1. Find exact log lines/events that SUPPORT the assertion
            2. Find exact log lines/events that CONTRADICT the assertion
            3. Cite with timestamps and sources
            4. If no evidence found, explicitly state MISSING

            Return structured citations only - no interpretation yet.
            """.formatted(
            request.assertion().claim(),
            request.assertion().validationQuestion(),
            String.join("\n", context.podLogs()),
            String.join("\n", context.kubernetesEvents()),
            String.join("\n", context.resourceUsage()),
            String.join("\n", context.kruizeRecommendations())
        );
    }
}

// Made with Bob
