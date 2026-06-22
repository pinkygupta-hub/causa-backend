package com.causa.validation.evaluation;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.causa.validation.ValidationAssertion;
import com.causa.validation.evidence.EvidenceExtractionResult;
import com.causa.validation.shared.AssertionValidationStatus;
import com.causa.validation.shared.EvidenceCitation;
import com.causa.validation.shared.ValidationDataSourceType;

/**
 * Unit tests for {@link MockAssertionValidator}.
 *
 * @since 0.0.1
 */
@DisplayName("MockAssertionValidator Tests")
class MockAssertionValidatorTest {

    private final MockAssertionValidator validator = new MockAssertionValidator();

    @Test
    @DisplayName("Should render prompt with assertion and formatted evidence")
    void shouldRenderPromptWithAssertionAndFormattedEvidence() {
        ValidationAssertion assertion = new ValidationAssertion(
            "The application crashed due to Java heap OutOfMemoryError.",
            "Do pod logs or JVM crash output explicitly show that the application terminated with Java heap OutOfMemoryError?",
            "Exact pod log or JVM fatal error lines stating Java heap OOM.",
            "Logs showing normal shutdown or non-memory-related fatal errors.",
            List.of("outofmemoryerror", "java heap space")
        );

        EvidenceCitation supporting = new EvidenceCitation(
            ValidationDataSourceType.POD_LOG,
            "pod-logs",
            "unknown",
            "Aborting due to java.lang.OutOfMemoryError: Java heap space"
        );

        EvidenceExtractionResult evidenceResult = new EvidenceExtractionResult(
            assertion.claim(),
            "mock prompt",
            List.of(supporting),
            List.of(),
            null
        );

        String prompt = AssertionValidationPromptTemplate.render(
            new AssertionValidationRequest(assertion, evidenceResult)
        );

        assertTrue(prompt.contains("You are a skeptical validator"));
        assertTrue(prompt.contains("ASSERTION TO VALIDATE:"));
        assertTrue(prompt.contains("The application crashed due to Java heap OutOfMemoryError."));
        assertTrue(prompt.contains("SUPPORTING EVIDENCE:"));
        assertTrue(prompt.contains("Aborting due to java.lang.OutOfMemoryError: Java heap space"));
        assertTrue(prompt.contains("DEFAULT TO UNSUPPORTED IF UNCERTAIN"));
        assertTrue(prompt.contains("Return JSON with status, confidence, and reasoning"));
    }

    @Test
    @DisplayName("Should return SUPPORTED with high confidence for direct OOM evidence")
    void shouldReturnSupportedWithHighConfidenceForDirectOomEvidence() {
        ValidationAssertion assertion = new ValidationAssertion(
            "The application crashed due to Java heap OutOfMemoryError.",
            "Do pod logs or JVM crash output explicitly show that the application terminated with Java heap OutOfMemoryError?",
            "Exact pod log or JVM fatal error lines stating Java heap OOM.",
            "Logs showing normal shutdown or non-memory-related fatal errors.",
            List.of("outofmemoryerror", "java heap space")
        );

        EvidenceCitation directEvidence = new EvidenceCitation(
            ValidationDataSourceType.POD_LOG,
            "pod-logs",
            "unknown",
            "Aborting due to java.lang.OutOfMemoryError: Java heap space"
        );

        EvidenceExtractionResult evidenceResult = new EvidenceExtractionResult(
            assertion.claim(),
            "mock prompt",
            List.of(directEvidence),
            List.of(),
            null
        );

        AssertionValidationResult result = validator.validate(
            new AssertionValidationRequest(assertion, evidenceResult)
        );

        assertEquals(AssertionValidationStatus.SUPPORTED, result.status());
        assertTrue(result.confidence() >= 75);
        assertTrue(result.reasoning().contains("SUPPORTED"));
        assertTrue(result.reasoning().contains("EXPLICIT"));
        assertTrue(result.reasoning().contains("OutOfMemoryError: Java heap space"));
    }

    @Test
    @DisplayName("Should return SUPPORTED for registry growth evidence")
    void shouldReturnSupportedForRegistryGrowthEvidence() {
        ValidationAssertion assertion = new ValidationAssertion(
            "Unbounded target registry growth was the primary cause of heap exhaustion.",
            "Do logs show sustained target registry growth leading up to the heap exhaustion event?",
            "Sequential log lines showing registry or target counts increasing rapidly before the OOM event.",
            "Evidence that registry size remained stable or bounded.",
            List.of("inserted", "current registry size", "targets=")
        );

        EvidenceCitation supporting = new EvidenceCitation(
            ValidationDataSourceType.POD_LOG,
            "pod-logs",
            "unknown",
            "2026-06-18 06:09:01,405 INFO [ai.causa.scheduler.DiscoveryScheduler] Inserted 159000 targets. Current registry size=159000"
        );

        EvidenceExtractionResult evidenceResult = new EvidenceExtractionResult(
            assertion.claim(),
            "mock prompt",
            List.of(supporting),
            List.of(),
            null
        );

        AssertionValidationResult result = validator.validate(
            new AssertionValidationRequest(assertion, evidenceResult)
        );

        assertEquals(AssertionValidationStatus.SUPPORTED, result.status());
        assertTrue(result.confidence() >= 75);
        assertTrue(result.reasoning().contains("SUPPORTED"));
    }

    @Test
    @DisplayName("Should return WEAKLY_SUPPORTED for insufficient memory limit with partial evidence")
    void shouldReturnWeaklySupportedForInsufficientMemoryLimitWithPartialEvidence() {
        ValidationAssertion assertion = new ValidationAssertion(
            "The configured 512 MiB memory limit was insufficient for the workload and is corroborated by Kruize recommendations.",
            "Do resource usage and Kruize recommendations indicate that the configured 512 MiB memory limit was insufficient for the workload?",
            "Resource usage near the configured memory limit together with Kruize recommendations increasing memory to a higher value.",
            "Low memory utilization or recommendations showing no increase needed.",
            List.of("memory 478/512", "806 mi", "kruize")
        );

        EvidenceCitation supporting = new EvidenceCitation(
            ValidationDataSourceType.RESOURCE_USAGE,
            "resource-usage",
            "unknown",
            "Memory usage at 93% capacity: 478/512 MiB"
        );

        EvidenceCitation contradicting = new EvidenceCitation(
            ValidationDataSourceType.KRUIZE_RECOMMENDATION,
            "kruize-recommendations",
            "unknown",
            "requests.cpu: 0.435"
        );

        EvidenceExtractionResult evidenceResult = new EvidenceExtractionResult(
            assertion.claim(),
            "mock prompt",
            List.of(supporting),
            List.of(contradicting),
            null
        );

        AssertionValidationResult result = validator.validate(
            new AssertionValidationRequest(assertion, evidenceResult)
        );

        assertEquals(AssertionValidationStatus.WEAKLY_SUPPORTED, result.status());
        assertTrue(result.confidence() >= 30 && result.confidence() <= 69);
        assertTrue(result.reasoning().contains("WEAKLY_SUPPORTED"));
        assertTrue(result.reasoning().contains("mixed signals"));
    }

    @Test
    @DisplayName("Should return UNSUPPORTED when no evidence exists")
    void shouldReturnUnsupportedWhenNoEvidenceExists() {
        ValidationAssertion assertion = new ValidationAssertion(
            "Serial GC was the primary root cause of the crash.",
            "Do available logs and context prove that Serial GC was the primary root cause?",
            "Direct GC configuration and causal evidence proving Serial GC alone caused the crash.",
            "Evidence showing another primary cause or no GC evidence.",
            List.of("serialold", "defnew", "parallelgcthreads=0")
        );

        EvidenceExtractionResult evidenceResult = new EvidenceExtractionResult(
            assertion.claim(),
            "mock prompt",
            List.of(),
            List.of(),
            "MISSING: No evidence found"
        );

        AssertionValidationResult result = validator.validate(
            new AssertionValidationRequest(assertion, evidenceResult)
        );

        assertEquals(AssertionValidationStatus.UNSUPPORTED, result.status());
        assertTrue(result.confidence() <= 29);
        assertTrue(result.reasoning().contains("UNSUPPORTED"));
        assertTrue(result.reasoning().contains("no exact supporting evidence"));
    }
}

// Made with Bob
