package com.causa.validation.evidence;

/**
 * Contract for Phase 2 evidence extraction from validation context.
 *
 * <p>Designed so a deterministic implementation can be replaced later by an
 * LLM-backed implementation without changing callers.
 *
 * @since 0.0.1
 */
public interface EvidenceExtractor {

    /**
     * Extracts exact evidence citations for a single assertion.
     *
     * @param request evidence extraction input
     * @return extracted supporting and contradicting evidence citations
     */
    EvidenceExtractionResult extractEvidence(EvidenceExtractionRequest request);
}

// Made with Bob
