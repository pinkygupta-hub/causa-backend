package com.causa.validation;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link DeterministicAssertionGenerator}.
 *
 * @since 0.0.1
 */
@DisplayName("DeterministicAssertionGenerator Tests")
class DeterministicAssertionGeneratorTest {

    private final DeterministicAssertionGenerator generator = new DeterministicAssertionGenerator();

    private static final String PROD_RCA = "The root cause is unbounded memory growth due to continuous insertion of targets into an in-memory registry without proper size limits or memory management. The application loaded 166,350 targets into memory, causing heap exhaustion. This was exacerbated by the use of Serial GC (single-threaded garbage collector) on what appears to be a multi-core system, leading to inefficient memory reclamation with long GC pause times (up to 597ms). The combination of rapid data accumulation, inadequate heap size (512 MiB limit), and inefficient GC configuration created a memory pressure scenario that culminated in OutOfMemoryError. The application's memory limit was insufficient for the workload, as evidenced by Kruize recommendations suggesting an increase to 806 MiB (+294 MiB).";

    @Test
    @DisplayName("Should render prompt with RCA statement only")
    void shouldRenderPromptWithRcaOnly() {
        AssertionGenerationRequest request = new AssertionGenerationRequest(PROD_RCA);

        String prompt = AssertionGenerationPromptTemplate.render(request);

        assertTrue(prompt.contains(PROD_RCA));
        assertTrue(prompt.contains("Decompose this RCA"));
        assertTrue(prompt.contains("Focus on the CLAIMS made in the RCA"));
        assertTrue(prompt.contains("Return as structured JSON array."));
    }

    @Test
    @DisplayName("Should generate single assertion (simple fallback behavior)")
    void shouldGenerateSingleAssertionAsFallback() {
        AssertionGenerationRequest request = new AssertionGenerationRequest(PROD_RCA);

        AssertionGenerationResult result = generator.generateAssertions(request);

        // Deterministic generator is simple fallback - treats entire RCA as one assertion
        // For real decomposition, use LLMAssertionGenerator
        assertEquals(1, result.assertions().size());

        ValidationAssertion assertion = result.assertions().get(0);
        assertEquals(PROD_RCA, assertion.claim());
        assertTrue(assertion.validationQuestion().contains("validate"));
    }

    @Test
    @DisplayName("Should keep single assertion when RCA does not match known patterns")
    void shouldKeepSingleAssertionWhenNoProductionPatternExists() {
        AssertionGenerationRequest request = new AssertionGenerationRequest(
            "Application restart observed after transient failure"
        );

        AssertionGenerationResult result = generator.generateAssertions(request);

        assertEquals(1, result.assertions().size());
        assertEquals("Application restart observed after transient failure", result.assertions().get(0).claim());
        assertTrue(result.assertions().get(0).validationQuestion().contains("directly validate"));
    }

    @Test
    @DisplayName("Should return empty assertions for blank RCA")
    void shouldReturnEmptyAssertionsForBlankRca() {
        AssertionGenerationRequest request = new AssertionGenerationRequest("   ");

        AssertionGenerationResult result = generator.generateAssertions(request);

        assertNotNull(result);
        assertEquals(List.of(), result.assertions());
    }

    @Test
    @DisplayName("Should treat any RCA as single assertion")
    void shouldTreatAnyRcaAsSingleAssertion() {
        String rcaWithOOM = "The application experienced an OutOfMemoryError leading to pod restart.";

        AssertionGenerationRequest request = new AssertionGenerationRequest(rcaWithOOM);

        AssertionGenerationResult result = generator.generateAssertions(request);

        assertEquals(1, result.assertions().size());
        assertEquals(rcaWithOOM, result.assertions().get(0).claim());
    }
}

// Made with Bob
