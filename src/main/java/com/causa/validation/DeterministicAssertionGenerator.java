package com.causa.validation;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import jakarta.enterprise.context.ApplicationScoped;

/**
 * Simple fallback assertion generator for testing.
 *
 * <p>Treats the entire RCA as a single assertion. This is NOT intelligent decomposition.
 * For production use, use {@link LLMAssertionGenerator} which calls an LLM to properly
 * decompose RCA statements into independent testable assertions.
 *
 * @since 0.0.1
 */
@ApplicationScoped
public class DeterministicAssertionGenerator implements AssertionGenerator {

    @Override
    public AssertionGenerationResult generateAssertions(AssertionGenerationRequest request) {
        String prompt = AssertionGenerationPromptTemplate.render(request);
        List<ValidationAssertion> assertions = buildAssertions(request.rcaStatement());
        return new AssertionGenerationResult(request.rcaStatement(), prompt, assertions);
    }

    private List<ValidationAssertion> buildAssertions(String rcaStatement) {
        String normalized = rcaStatement == null ? "" : rcaStatement.trim();
        if (normalized.isBlank()) {
            return List.of();
        }

        // Simple fallback: treat entire RCA as single assertion
        // For real decomposition, use LLMAssertionGenerator
        List<ValidationAssertion> assertions = new ArrayList<>();
        assertions.add(createAssertion(normalized));
        return assertions;
    }

    private ValidationAssertion createAssertion(String claim) {
        String lowerClaim = claim.toLowerCase(Locale.ROOT);

        if (lowerClaim.contains("java heap outofmemoryerror") || lowerClaim.contains("heap outofmemoryerror")) {
            return new ValidationAssertion(
                claim,
                "Do pod logs or JVM crash output explicitly show that the application terminated with Java heap OutOfMemoryError?",
                "Exact pod log or JVM fatal error lines stating 'java.lang.OutOfMemoryError: Java heap space' or equivalent heap OOM crash output.",
                "Logs showing normal shutdown, non-memory-related fatal errors, or absence of any heap OOM signal.",
                List.of("outofmemoryerror", "java heap space", "fatal error", "aborting due to", "outofmemory encountered")
            );
        }

        if (lowerClaim.contains("target registry growth") || lowerClaim.contains("heap exhaustion")) {
            return new ValidationAssertion(
                claim,
                "Do logs show sustained target registry growth leading up to the heap exhaustion event?",
                "Sequential log lines showing registry or target counts increasing rapidly before the OOM event, indicating unbounded in-memory growth.",
                "Evidence that registry size remained stable, was bounded, or that OOM occurred without significant target accumulation.",
                List.of("inserted", "current registry size", "targets=", "registry size", "discoveryscheduler", "scrapescheduler")
            );
        }

        if (lowerClaim.contains("memory limit was insufficient") || lowerClaim.contains("kruize recommendations") || lowerClaim.contains("performance recommendations")) {
            return new ValidationAssertion(
                claim,
                "Do resource usage and performance recommendations indicate that the configured memory limit was insufficient for the workload?",
                "Resource usage near the configured memory limit together with performance recommendations (e.g., Kruize) suggesting memory increase.",
                "Low memory utilization, recommendations showing no increase needed, or evidence that the workload fit comfortably within the configured limit.",
                List.of("memory", "mib", "mi", "limits.memory", "requests.memory", "kruize", "recommendation")
            );
        }

        return new ValidationAssertion(
            claim,
            "What logs, events, or metrics directly validate this RCA claim?",
            "Direct evidence in logs, events, or metrics that explicitly matches the claim and its timing.",
            "Evidence showing the opposite condition, or absence of any direct signal supporting the claim.",
            deriveKeywords(claim)
        );
    }

    private List<String> deriveKeywords(String claim) {
        return List.of(
            claim.toLowerCase(Locale.ROOT)
                .replace(",", " ")
                .replace(".", " ")
                .replace(":", " ")
                .trim()
        );
    }
}

// Made with Bob
