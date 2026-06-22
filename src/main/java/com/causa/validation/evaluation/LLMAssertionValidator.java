package com.causa.validation.evaluation;

import com.causa.core.domain.LLMRequest;
import com.causa.core.domain.LLMResponse;
import com.causa.core.ports.llm.PromptSender;
import com.causa.validation.shared.AssertionValidationStatus;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * LLM-powered Phase 3 assertion validator.
 *
 * <p>Uses Bob as a skeptical critic to validate assertions against evidence.
 *
 * @since 0.0.1
 */
@ApplicationScoped
public class LLMAssertionValidator implements AssertionValidator {

    private final PromptSender promptSender;
    private final ObjectMapper objectMapper;

    private static final String SYSTEM_PROMPT = """
        You are a skeptical evidence validator.

        CRITICAL RULES:
        - Evaluate ONLY the provided evidence (no external knowledge)
        - DEFAULT to UNSUPPORTED if uncertain
        - Distinguish: EXPLICIT (log states it) vs IMPLICIT (log suggests) vs INFERRED (guessed)
        - Quote exact log lines in your reasoning
        - Confidence calibration:
            90-100: Overwhelming, unambiguous evidence
            70-89: Strong evidence, minor gaps
            50-69: Moderate evidence, some uncertainty
            30-49: Weak evidence or mixed signals
            0-29: Insufficient or contradictory evidence

        Return ONLY valid JSON:
        {
          "status": "SUPPORTED",
          "confidence": 95,
          "reasoning": "SUPPORTED: EXPLICIT evidence found. Exact line: '...'"
        }

        Status must be one of: SUPPORTED, WEAKLY_SUPPORTED, UNSUPPORTED

        Do NOT include any text before or after the JSON.
        """;

    @Inject
    public LLMAssertionValidator(PromptSender promptSender, ObjectMapper objectMapper) {
        this.promptSender = promptSender;
        this.objectMapper = objectMapper;
    }

    @Override
    public AssertionValidationResult validate(AssertionValidationRequest request) {
        String prompt = AssertionValidationPromptTemplate.render(request);

        // Call LLM (Bob as Skeptical Critic)
        LLMRequest llmRequest = LLMRequest.builder(prompt)
            .systemPrompt(SYSTEM_PROMPT)
            .temperature(0.1)  // Very consistent judgments
            .maxTokens(1000)
            .build();

        LLMResponse llmResponse = promptSender.send(llmRequest);

        // Parse response
        return parseValidationResult(llmResponse.responseText(), request.assertion().claim(), prompt);
    }

    private AssertionValidationResult parseValidationResult(String jsonContent, String assertionClaim, String prompt) {
        try {
            String cleanJson = extractJson(jsonContent);
            JsonNode root = objectMapper.readTree(cleanJson);

            AssertionValidationStatus status = AssertionValidationStatus.valueOf(
                root.get("status").asText()
            );
            int confidence = root.get("confidence").asInt();
            String reasoning = root.get("reasoning").asText();

            return new AssertionValidationResult(
                assertionClaim,
                prompt,
                status,
                confidence,
                reasoning
            );

        } catch (Exception e) {
            throw new RuntimeException("Failed to parse LLM validation response: " + e.getMessage(), e);
        }
    }

    private String extractJson(String content) {
        int start = content.indexOf('{');
        int end = content.lastIndexOf('}');

        if (start >= 0 && end > start) {
            return content.substring(start, end + 1);
        }

        return content;
    }
}

// Made with Bob
