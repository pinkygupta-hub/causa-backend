package com.causa.validation.evaluation;

import java.util.List;

import com.causa.validation.shared.EvidenceCitation;

/**
 * Renders the Phase 3 prompt template for skeptical assertion validation.
 *
 * @since 0.0.1
 */
public final class AssertionValidationPromptTemplate {

    private AssertionValidationPromptTemplate() {
        throw new AssertionError("Utility class cannot be instantiated");
    }

    /**
     * Renders the prompt template using the provided request.
     *
     * @param request assertion validation input
     * @return rendered prompt text
     */
    public static String render(AssertionValidationRequest request) {
        String formattedSupportingEvidence = formatEvidence(request.evidenceResult().supportingEvidence());
        String formattedContradictingEvidence = formatEvidence(request.evidenceResult().contradictingEvidence());
        
        return """
            You are a skeptical validator. Evaluate ONLY the provided evidence.

            ASSERTION TO VALIDATE:
            %s

            SUPPORTING EVIDENCE:
            %s

            CONTRADICTING EVIDENCE:
            %s

            Your task:
            1. Assess if supporting evidence is direct or circumstantial
            2. Check if contradicting evidence invalidates the claim
            3. Identify critical missing evidence
            4. Assign status: SUPPORTED / WEAKLY_SUPPORTED / UNSUPPORTED
            5. Provide confidence score (0-100) with calibration:
               - 90-100: Evidence is overwhelming and unambiguous
               - 70-89: Strong evidence with minor gaps
               - 50-69: Moderate evidence, some uncertainty
               - 30-49: Weak evidence or mixed signals
               - 0-29: Insufficient or contradictory evidence

            DEFAULT TO UNSUPPORTED IF UNCERTAIN.

            IMPORTANT: If you see log line X as evidence, quote the EXACT line.
            If you're inferring something not explicitly stated, mark it as 'inference'.
            Distinguish between:
            - EXPLICIT: Log says 'CNI config missing'
            - IMPLICIT: Log shows network timeout, might imply CNI issue
            - INFERRED: No CNI logs at all, could mean missing config

            Return JSON with status, confidence, and reasoning.
            """.formatted(
            request.assertion().claim(),
            formattedSupportingEvidence,
            formattedContradictingEvidence
        );
    }

    private static String formatEvidence(List<EvidenceCitation> evidence) {
        if (evidence.isEmpty()) {
            return "(No evidence found)";
        }
        
        StringBuilder formatted = new StringBuilder();
        for (EvidenceCitation citation : evidence) {
            formatted.append("- [")
                    .append(citation.timestamp())
                    .append("] ")
                    .append(citation.sourceName())
                    .append(": ")
                    .append(citation.excerpt())
                    .append("\n");
        }
        return formatted.toString().trim();
    }
}

// Made with Bob
