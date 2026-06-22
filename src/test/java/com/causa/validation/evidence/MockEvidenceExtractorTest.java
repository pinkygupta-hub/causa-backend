package com.causa.validation.evidence;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.causa.validation.ValidationAssertion;
import com.causa.validation.shared.MockValidationContext;
import com.causa.validation.shared.ValidationDataSourceType;

/**
 * Unit tests for {@link MockEvidenceExtractor}.
 *
 * @since 0.0.1
 */
@DisplayName("MockEvidenceExtractor Tests")
class MockEvidenceExtractorTest {

    private final MockEvidenceExtractor extractor = new MockEvidenceExtractor();

    private MockValidationContext productionLikeContext() {
        return new MockValidationContext(
            List.of(
                "2026-06-18 06:09:01,405 INFO [ai.causa.scheduler.DiscoveryScheduler] Inserted 159000 targets. Current registry size=159000",
                "2026-06-18 06:09:04,839 INFO [ai.causa.scheduler.ScrapeScheduler] Scrape started. targets=166324",
                "2026-06-18 06:09:04,839 INFO [ai.causa.scheduler.ScrapeScheduler] Writer started. targets=166350",
                "Aborting due to java.lang.OutOfMemoryError: Java heap space",
                "# fatal error: OutOfMemory encountered: Java heap space"
            ),
            List.of(
                "[Normal] 2026-06-18 06:03:32 +0000 UTC: AddedInterface - Add eth0 [10.129.2.41/23] from ovn-kubernetes",
                "[Normal] 2026-06-18 06:07:26 +0000 UTC: Started - Started container heap-oom-prom",
                "[Normal] 2026-06-18 06:09:13 +0000 UTC: BackOff - Back-off restarting failed container heap-oom-prom"
            ),
            List.of(
                "CPU 0.421/0.500",
                "MEMORY 478/512",
                "Memory usage at 93% capacity: 478/512 MiB"
            ),
            List.of(
                "requests.cpu: 0.435",
                "requests.memory: 806 Mi",
                "limits.cpu: 0.435",
                "limits.memory: 806 Mi",
                "Kruize recommends memory increase from 512 MiB to 806 MiB (+294 MiB / +57%)"
            )
        );
    }

    @Test
    @DisplayName("Should render prompt with assertion and production-like mock validation context")
    void shouldRenderPromptWithAssertionAndContext() {
        ValidationAssertion assertion = new ValidationAssertion(
            "The application crashed due to Java heap OutOfMemoryError.",
            "Do pod logs or JVM crash output explicitly show that the application terminated with Java heap OutOfMemoryError?",
            "Exact pod log or JVM fatal error lines stating Java heap OOM.",
            "Logs showing normal shutdown or non-memory-related fatal errors.",
            List.of("outofmemoryerror", "java heap space", "fatal error")
        );

        EvidenceExtractionRequest request = new EvidenceExtractionRequest(assertion, productionLikeContext());

        String prompt = EvidenceExtractionPromptTemplate.render(request);

        assertTrue(prompt.contains("Assertion: The application crashed due to Java heap OutOfMemoryError."));
        assertTrue(prompt.contains("--- POD LOGS ---"));
        assertTrue(prompt.contains("--- KUBERNETES EVENTS ---"));
        assertTrue(prompt.contains("--- RESOURCE USAGE ---"));
        assertTrue(prompt.contains("--- KRUIZE RECOMMENDATIONS ---"));
        assertTrue(prompt.contains("Return structured citations only - no interpretation yet."));
    }

    @Test
    @DisplayName("Should extract supporting evidence for Java heap OOM assertion")
    void shouldExtractSupportingEvidenceForJavaHeapOomAssertion() {
        ValidationAssertion assertion = new ValidationAssertion(
            "The application crashed due to Java heap OutOfMemoryError.",
            "Do pod logs or JVM crash output explicitly show that the application terminated with Java heap OutOfMemoryError?",
            "Exact pod log or JVM fatal error lines stating Java heap OOM.",
            "Logs showing normal shutdown or non-memory-related fatal errors.",
            List.of("outofmemoryerror", "java heap space", "fatal error")
        );

        EvidenceExtractionResult result = extractor.extractEvidence(new EvidenceExtractionRequest(assertion, productionLikeContext()));

        assertEquals("The application crashed due to Java heap OutOfMemoryError.", result.assertionClaim());
        assertEquals(2, result.supportingEvidence().size());
        assertTrue(result.contradictingEvidence().isEmpty());
        assertNull(result.missingEvidenceNote());

        assertEquals(ValidationDataSourceType.POD_LOG, result.supportingEvidence().get(0).sourceType());
        assertTrue(result.supportingEvidence().get(0).excerpt().contains("OutOfMemoryError"));
    }

    @Test
    @DisplayName("Should extract supporting evidence for unbounded registry growth assertion")
    void shouldExtractSupportingEvidenceForRegistryGrowthAssertion() {
        ValidationAssertion assertion = new ValidationAssertion(
            "Unbounded target registry growth was the primary cause of heap exhaustion.",
            "Do logs show sustained target registry growth leading up to the heap exhaustion event?",
            "Sequential log lines showing registry or target counts increasing rapidly before the OOM event.",
            "Evidence that registry size remained stable or bounded.",
            List.of("inserted", "current registry size", "targets=", "registry size")
        );

        EvidenceExtractionResult result = extractor.extractEvidence(new EvidenceExtractionRequest(assertion, productionLikeContext()));

        assertTrue(result.supportingEvidence().size() >= 2);
        assertTrue(result.contradictingEvidence().isEmpty());
        assertNull(result.missingEvidenceNote());
        assertEquals(ValidationDataSourceType.POD_LOG, result.supportingEvidence().get(0).sourceType());
        assertTrue(result.supportingEvidence().stream().anyMatch(citation -> citation.excerpt().contains("targets=166324")));
    }

    @Test
    @DisplayName("Should extract supporting evidence for insufficient memory limit assertion")
    void shouldExtractSupportingEvidenceForInsufficientMemoryLimitAssertion() {
        ValidationAssertion assertion = new ValidationAssertion(
            "The configured 512 MiB memory limit was insufficient for the workload and is corroborated by Kruize recommendations.",
            "Do resource usage and Kruize recommendations indicate that the configured 512 MiB memory limit was insufficient for the workload?",
            "Resource usage near the configured memory limit together with Kruize recommendations increasing memory to a higher value.",
            "Low memory utilization or recommendations showing no increase needed.",
            List.of("memory 478/512", "512 mib", "806 mi", "limits.memory", "kruize")
        );

        EvidenceExtractionResult result = extractor.extractEvidence(new EvidenceExtractionRequest(assertion, productionLikeContext()));

        assertTrue(result.supportingEvidence().size() >= 3);
        assertTrue(result.contradictingEvidence().isEmpty());
        assertNull(result.missingEvidenceNote());
        assertTrue(result.supportingEvidence().stream().anyMatch(citation -> citation.sourceType() == ValidationDataSourceType.RESOURCE_USAGE));
        assertTrue(result.supportingEvidence().stream().anyMatch(citation -> citation.sourceType() == ValidationDataSourceType.KRUIZE_RECOMMENDATION));
    }

    @Test
    @DisplayName("Should return missing note when no evidence is found")
    void shouldReturnMissingNoteWhenNoEvidenceIsFound() {
        ValidationAssertion assertion = new ValidationAssertion(
            "Serial GC was the primary root cause of the crash.",
            "Do available logs and context prove that Serial GC was the primary root cause?",
            "Direct GC configuration and causal evidence proving Serial GC alone caused the crash.",
            "Evidence showing another primary cause or no GC evidence.",
            List.of("serialold", "defnew", "parallelgcthreads=0")
        );

        EvidenceExtractionResult result = extractor.extractEvidence(new EvidenceExtractionRequest(assertion, productionLikeContext()));

        assertTrue(result.supportingEvidence().isEmpty());
        assertTrue(result.contradictingEvidence().isEmpty());
        assertNotNull(result.missingEvidenceNote());
        assertTrue(result.missingEvidenceNote().contains("MISSING"));
    }
}

// Made with Bob
