package com.causa.validation;

import java.util.List;
import java.util.Objects;

/**
 * Independent assertion generated from an RCA statement for later validation.
 *
 * @param claim assertion claim text
 * @param validationQuestion targeted question used to validate the claim
 * @param supportingEvidenceDescription description of evidence that would support the claim
 * @param contradictingEvidenceDescription description of evidence that would contradict the claim
 * @param searchKeywords keywords to search in logs or metrics
 * @since 0.0.1
 */
public record ValidationAssertion(
    String claim,
    String validationQuestion,
    String supportingEvidenceDescription,
    String contradictingEvidenceDescription,
    List<String> searchKeywords
) {

    public ValidationAssertion {
        Objects.requireNonNull(claim, "claim cannot be null");
        Objects.requireNonNull(validationQuestion, "validationQuestion cannot be null");
        Objects.requireNonNull(supportingEvidenceDescription, "supportingEvidenceDescription cannot be null");
        Objects.requireNonNull(contradictingEvidenceDescription, "contradictingEvidenceDescription cannot be null");
        searchKeywords = searchKeywords == null ? List.of() : List.copyOf(searchKeywords);
    }
}

// Made with Bob
