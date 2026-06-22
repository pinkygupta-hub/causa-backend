package com.causa.validation.evidence;

import java.util.List;
import java.util.Objects;

import com.causa.validation.shared.EvidenceCitation;

/**
 * Phase 2 result model containing extracted evidence citations for a single assertion.
 *
 * @param assertionClaim original assertion claim text
 * @param promptTemplate rendered prompt template for future LLM integration
 * @param supportingEvidence exact citations that support the assertion
 * @param contradictingEvidence exact citations that contradict the assertion
 * @param missingEvidenceNote explicit note if no evidence was found
 * @since 0.0.1
 */
public record EvidenceExtractionResult(
    String assertionClaim,
    String promptTemplate,
    List<EvidenceCitation> supportingEvidence,
    List<EvidenceCitation> contradictingEvidence,
    String missingEvidenceNote
) {

    public EvidenceExtractionResult {
        Objects.requireNonNull(assertionClaim, "assertionClaim cannot be null");
        Objects.requireNonNull(promptTemplate, "promptTemplate cannot be null");
        supportingEvidence = supportingEvidence == null ? List.of() : List.copyOf(supportingEvidence);
        contradictingEvidence = contradictingEvidence == null ? List.of() : List.copyOf(contradictingEvidence);
        // missingEvidenceNote can be null
    }
}

// Made with Bob
