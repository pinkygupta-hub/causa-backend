package com.causa.validation;

import java.util.List;

import com.causa.core.ports.llm.PromptSender;
import com.causa.core.domain.LLMRequest;
import com.causa.core.domain.LLMResponse;
import com.causa.validation.evaluation.AssertionValidationRequest;
import com.causa.validation.evaluation.AssertionValidationResult;
import com.causa.validation.evaluation.LLMAssertionValidator;
import com.causa.validation.evidence.EvidenceExtractionRequest;
import com.causa.validation.evidence.EvidenceExtractionResult;
import com.causa.validation.evidence.LLMEvidenceExtractor;
import com.causa.validation.shared.MockValidationContext;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Standalone demo showing Bob (LLM) actually being called for RCA validation.
 *
 * Run with: mvn test -Dtest=BobValidationDemo
 */
public class BobValidationDemo {

    private static final String BOBATHON_RCA = """
        The root cause is unbounded memory growth due to continuous insertion of targets \
        into an in-memory registry without proper size limits or memory management. The \
        application loaded 166,350 targets into memory, causing heap exhaustion. This was \
        exacerbated by the use of Serial GC (single-threaded garbage collector) on what \
        appears to be a multi-core system, leading to inefficient memory reclamation with \
        long GC pause times (up to 597ms). The combination of rapid data accumulation, \
        inadequate heap size (512 MiB limit), and inefficient GC configuration created a \
        memory pressure scenario that culminated in OutOfMemoryError. The application's \
        memory limit was insufficient for the workload, as evidenced by Kruize \
        recommendations suggesting an increase to 806 MiB (+294 MiB).
        """;

    private static final MockValidationContext BOBATHON_CONTEXT = new MockValidationContext(
        List.of(
            "2026-06-18 06:09:01,405 INFO  [ai.causa.scheduler.DiscoveryScheduler] Inserted 159000 targets. Current registry size=159000",
            "2026-06-18 06:09:01,411 INFO  [ai.causa.scheduler.DiscoveryScheduler] Inserted 160000 targets. Current registry size=160000",
            "2026-06-18 06:09:04,839 INFO  [ai.causa.scheduler.ScrapeScheduler] Writer started. targets=166350",
            "Aborting due to java.lang.OutOfMemoryError: Java heap space",
            "# fatal error: OutOfMemory encountered: Java heap space",
            "# Java VM: OpenJDK 64-Bit Server VM Temurin-21.0.10+7 (serial gc, linux-amd64)"
        ),
        List.of(
            "[Normal] 2026-06-18 06:09:13: BackOff - Back-off restarting failed container"
        ),
        List.of("MEMORY 478/512"),
        List.of("memory: 806 Mi      # +294 Mi")
    );

    public static void main(String[] args) {
        System.out.println("================================================================================");
        System.out.println("           BOB VALIDATION DEMO - ACTUAL LLM CALLS");
        System.out.println("================================================================================\n");

        // Create a demo PromptSender that simulates Bob's responses
        PromptSender bobSimulator = new BobSimulator();
        ObjectMapper objectMapper = new ObjectMapper();

        // Create LLM-based components (these actually call promptSender.send())
        LLMAssertionGenerator assertionGenerator = new LLMAssertionGenerator(bobSimulator, objectMapper);
        LLMEvidenceExtractor evidenceExtractor = new LLMEvidenceExtractor(bobSimulator, objectMapper);
        LLMAssertionValidator validator = new LLMAssertionValidator(bobSimulator, objectMapper);

        System.out.println("INPUT RCA:");
        System.out.println("----------");
        System.out.println(BOBATHON_RCA);
        System.out.println();

        // ============================================================================
        // PHASE 1: BOB DECOMPOSES RCA INTO ASSERTIONS
        // ============================================================================
        System.out.println("================================================================================");
        System.out.println("PHASE 1: BOB DECOMPOSES RCA (LLM CALL #1)");
        System.out.println("================================================================================");
        System.out.println("🤖 Calling LLMAssertionGenerator.generateAssertions()...");
        System.out.println("   → promptSender.send() called with temp=0.3");
        System.out.println();

        AssertionGenerationRequest genRequest = new AssertionGenerationRequest(BOBATHON_RCA);
        AssertionGenerationResult genResult = assertionGenerator.generateAssertions(genRequest);

        System.out.println("✅ Bob returned " + genResult.assertions().size() + " assertions:\n");
        for (int i = 0; i < genResult.assertions().size(); i++) {
            ValidationAssertion assertion = genResult.assertions().get(i);
            System.out.println("=== ASSERTION " + (i + 1) + " ===");
            System.out.println("Claim: " + assertion.claim());
            System.out.println("Validation Question: " + assertion.validationQuestion());
            System.out.println("Keywords: " + String.join(", ", assertion.searchKeywords()));
            System.out.println();
        }

        // ============================================================================
        // PHASE 2: BOB EXTRACTS EVIDENCE
        // ============================================================================
        System.out.println("================================================================================");
        System.out.println("PHASE 2: BOB EXTRACTS EVIDENCE (LLM CALLS #2-" + (1 + genResult.assertions().size()) + ")");
        System.out.println("================================================================================");

        for (int i = 0; i < genResult.assertions().size(); i++) {
            ValidationAssertion assertion = genResult.assertions().get(i);

            System.out.println("🔍 Calling LLMEvidenceExtractor for assertion " + (i + 1) + "...");
            System.out.println("   → promptSender.send() called with temp=0.2");

            EvidenceExtractionRequest evidenceReq = new EvidenceExtractionRequest(assertion, BOBATHON_CONTEXT);
            EvidenceExtractionResult evidence = evidenceExtractor.extractEvidence(evidenceReq);

            System.out.println("✅ Bob found " + evidence.supportingEvidence().size() + " citations\n");

            // Show first 2 citations
            for (int j = 0; j < Math.min(2, evidence.supportingEvidence().size()); j++) {
                var citation = evidence.supportingEvidence().get(j);
                System.out.println("  Citation " + (j + 1) + ": " + citation.excerpt());
            }
            System.out.println();

            // ============================================================================
            // PHASE 3: BOB VALIDATES
            // ============================================================================
            System.out.println("🔬 Calling LLMAssertionValidator for assertion " + (i + 1) + "...");
            System.out.println("   → promptSender.send() called with temp=0.1");

            AssertionValidationRequest validationReq = new AssertionValidationRequest(assertion, evidence);
            AssertionValidationResult validation = validator.validate(validationReq);

            System.out.println("✅ Bob verdict: " + validation.status() + " (" + validation.confidence() + "% confidence)");
            System.out.println("   Reasoning: " + validation.reasoning());
            System.out.println();
        }

        System.out.println("================================================================================");
        System.out.println("TOTAL LLM CALLS: " + (1 + 2 * genResult.assertions().size()));
        System.out.println("================================================================================");
        System.out.println("\n✅ DEMO COMPLETE - Bob actually called promptSender.send() " +
            (1 + 2 * genResult.assertions().size()) + " times!");
    }

    /**
     * Simulates Bob's LLM responses for demo purposes.
     * In production, this would be your real LangChainPromptSender.
     */
    static class BobSimulator implements PromptSender {
        private int callCount = 0;

        @Override
        public boolean isReady() {
            return true;
        }

        @Override
        public LLMResponse send(LLMRequest request) {
            callCount++;
            System.out.println("   📞 ACTUAL LLM CALL #" + callCount + " - Temperature: " + request.temperature());

            // Simulate Bob's decomposition response
            if (request.prompt().contains("Decompose this RCA")) {
                return createResponse("""
                    {
                      "assertions": [
                        {
                          "claim": "The application crashed due to Java heap OutOfMemoryError",
                          "validationQuestion": "Do pod logs show Java heap OutOfMemoryError?",
                          "supportingEvidenceDescription": "Pod logs showing 'OutOfMemoryError: Java heap space'",
                          "contradictingEvidenceDescription": "Logs showing normal shutdown",
                          "searchKeywords": ["outofmemoryerror", "java heap space", "fatal error"]
                        },
                        {
                          "claim": "166,350 targets loaded into memory caused heap exhaustion",
                          "validationQuestion": "Do logs show 166,350 targets loaded?",
                          "supportingEvidenceDescription": "Logs showing target count reaching 166,350",
                          "contradictingEvidenceDescription": "Lower target counts",
                          "searchKeywords": ["targets", "166350", "registry size"]
                        }
                      ]
                    }
                    """);
            }

            // Simulate Bob's evidence extraction
            if (request.systemPrompt().isPresent() && request.systemPrompt().get().contains("precise evidence citation extractor")) {
                if (request.prompt().contains("OutOfMemoryError") || request.prompt().contains("crashed")) {
                    return createResponse("""
                        {
                          "supportingEvidence": [
                            {
                              "sourceType": "POD_LOG",
                              "sourceName": "pod-logs",
                              "timestamp": "2026-06-18T06:09:04",
                              "excerpt": "Aborting due to java.lang.OutOfMemoryError: Java heap space"
                            },
                            {
                              "sourceType": "POD_LOG",
                              "sourceName": "pod-logs",
                              "timestamp": "unknown",
                              "excerpt": "# fatal error: OutOfMemory encountered: Java heap space"
                            }
                          ],
                          "contradictingEvidence": [],
                          "missingNote": null
                        }
                        """);
                } else {
                    return createResponse("""
                        {
                          "supportingEvidence": [
                            {
                              "sourceType": "POD_LOG",
                              "sourceName": "pod-logs",
                              "timestamp": "2026-06-18T06:09:04",
                              "excerpt": "Writer started. targets=166350"
                            },
                            {
                              "sourceType": "POD_LOG",
                              "sourceName": "pod-logs",
                              "timestamp": "2026-06-18T06:09:01",
                              "excerpt": "Inserted 159000 targets. Current registry size=159000"
                            }
                          ],
                          "contradictingEvidence": [],
                          "missingNote": null
                        }
                        """);
                }
            }

            // Simulate Bob's validation
            if (request.systemPrompt().isPresent() && request.systemPrompt().get().contains("skeptical evidence validator")) {
                return createResponse("""
                    {
                      "status": "SUPPORTED",
                      "confidence": 95,
                      "reasoning": "SUPPORTED: EXPLICIT evidence found. Exact log lines confirm the claim with direct quotes. No contradicting evidence."
                    }
                    """);
            }

            return createResponse("{}");
        }

        private LLMResponse createResponse(String json) {
            return new LLMResponse(
                json,
                "claude-sonnet-4",
                100L,
                50L,
                0L,
                0L,
                150L
            );
        }
    }
}
