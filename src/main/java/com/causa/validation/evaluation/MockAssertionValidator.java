package com.causa.validation.evaluation;

import java.util.List;

import com.causa.validation.shared.AssertionValidationStatus;
import com.causa.validation.shared.EvidenceCitation;

import jakarta.enterprise.context.ApplicationScoped;

/**
 * Deterministic Phase 3 assertion validator.
 *
 * <p>Uses skeptical rule-based scoring while preserving the same contract
 * expected by a future LLM-backed implementation.
 *
 * @since 0.0.1
 */
@ApplicationScoped
public class MockAssertionValidator implements AssertionValidator {

    @Override
    public AssertionValidationResult validate(AssertionValidationRequest request) {
        String prompt = AssertionValidationPromptTemplate.render(request);
        List<EvidenceCitation> supporting = request.evidenceResult().supportingEvidence();
        List<EvidenceCitation> contradicting = request.evidenceResult().contradictingEvidence();

        AssertionValidationStatus status;
        int confidence;
        String reasoning;

        if (supporting.isEmpty()) {
            status = AssertionValidationStatus.UNSUPPORTED;
            confidence = contradicting.isEmpty() ? 15 : 10;
            reasoning = buildUnsupportedReasoning(request, contradicting);
        } else if (!contradicting.isEmpty()) {
            status = AssertionValidationStatus.WEAKLY_SUPPORTED;
            confidence = calculateWeakConfidence(supporting.size(), contradicting.size());
            reasoning = buildWeakReasoning(supporting, contradicting);
        } else {
            boolean directEvidence = hasDirectEvidence(supporting, request.assertion().claim());
            status = directEvidence ? AssertionValidationStatus.SUPPORTED : AssertionValidationStatus.WEAKLY_SUPPORTED;
            confidence = directEvidence
                ? calculateSupportedConfidence(supporting.size())
                : calculateImplicitConfidence(supporting.size());
            reasoning = buildPositiveReasoning(supporting, directEvidence);
        }

        return new AssertionValidationResult(
            request.assertion().claim(),
            prompt,
            status,
            confidence,
            reasoning
        );
    }

    private boolean hasDirectEvidence(List<EvidenceCitation> supporting, String claim) {
        String normalizedClaim = claim.toLowerCase();
        return supporting.stream()
            .map(EvidenceCitation::excerpt)
            .map(String::toLowerCase)
            .anyMatch(excerpt ->
                excerpt.contains("oomkilled") ||
                excerpt.contains("cni config missing") ||
                excerpt.contains("network not ready") ||
                excerpt.contains(normalizedClaim)
            );
    }

    private int calculateSupportedConfidence(int supportingCount) {
        return Math.min(95, 75 + (supportingCount * 5));
    }

    private int calculateImplicitConfidence(int supportingCount) {
        return Math.min(69, 50 + (supportingCount * 4));
    }

    private int calculateWeakConfidence(int supportingCount, int contradictingCount) {
        int score = 55 + (supportingCount * 4) - (contradictingCount * 8);
        return Math.max(30, Math.min(69, score));
    }

    private String buildUnsupportedReasoning(AssertionValidationRequest request, List<EvidenceCitation> contradicting) {
        if (!contradicting.isEmpty()) {
            return "UNSUPPORTED: contradicting evidence exists and no exact supporting citation was found. " +
                "Defaulted to unsupported due to uncertainty. Exact contradicting line: \"" +
                contradicting.get(0).excerpt() + "\"";
        }

        return "UNSUPPORTED: no exact supporting evidence was found for the assertion. " +
            "Any conclusion would be inference. Critical missing evidence remains unresolved.";
    }

    private String buildWeakReasoning(List<EvidenceCitation> supporting, List<EvidenceCitation> contradicting) {
        return "WEAKLY_SUPPORTED: mixed signals detected. Supporting evidence includes exact line \"" +
            supporting.get(0).excerpt() + "\" but contradicting evidence includes exact line \"" +
            contradicting.get(0).excerpt() + "\". Conclusion remains partially inference due to conflict.";
    }

    private String buildPositiveReasoning(List<EvidenceCitation> supporting, boolean directEvidence) {
        String evidenceType = directEvidence ? "EXPLICIT" : "IMPLICIT";
        String supportLevel = directEvidence ? "SUPPORTED" : "WEAKLY_SUPPORTED";
        String inferenceNote = directEvidence
            ? "Evidence is direct rather than inferred."
            : "Evidence is circumstantial and includes inference.";

        return supportLevel + ": " + evidenceType + " supporting evidence found. Exact line: \"" +
            supporting.get(0).excerpt() + "\". " + inferenceNote;
    }
}

// Made with Bob
