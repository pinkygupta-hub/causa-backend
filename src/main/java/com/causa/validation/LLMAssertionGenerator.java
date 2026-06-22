package com.causa.validation;

import com.causa.core.domain.LLMRequest;
import com.causa.core.domain.LLMResponse;
import com.causa.core.ports.llm.PromptSender;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.List;

/**
 * LLM-powered Phase 1 assertion generator.
 *
 * <p>Uses an LLM to decompose RCA statements into independent assertions.
 * This is the production implementation that calls Bob.
 *
 * @since 0.0.1
 */
@ApplicationScoped
public class LLMAssertionGenerator implements AssertionGenerator {

    private final PromptSender promptSender;
    private final ObjectMapper objectMapper;

    private static final String SYSTEM_PROMPT = """
        You are an expert diagnostic assertion decomposer.

        Your task: Break RCA statements into 2-5 independent, testable assertions.

        Rules:
        - Each assertion must be independently provable/disprovable
        - Focus on CLAIMS made in the RCA, not evidence analysis
        - Generate targeted validation questions
        - Specify what evidence would support/contradict each claim
        - Extract search keywords for log scanning

        Return ONLY valid JSON matching this exact structure:
        {
          "assertions": [
            {
              "claim": "The specific claim from the RCA",
              "validationQuestion": "How to verify this claim?",
              "supportingEvidenceDescription": "What evidence would prove it",
              "contradictingEvidenceDescription": "What evidence would disprove it",
              "searchKeywords": ["keyword1", "keyword2"]
            }
          ]
        }

        Do NOT include any text before or after the JSON.
        """;

    @Inject
    public LLMAssertionGenerator(PromptSender promptSender, ObjectMapper objectMapper) {
        this.promptSender = promptSender;
        this.objectMapper = objectMapper;
    }

    @Override
    public AssertionGenerationResult generateAssertions(AssertionGenerationRequest request) {
        String prompt = AssertionGenerationPromptTemplate.render(request);

        // Call LLM
        LLMRequest llmRequest = LLMRequest.builder(prompt)
            .systemPrompt(SYSTEM_PROMPT)
            .temperature(0.3)  // Deterministic decomposition
            .maxTokens(2000)
            .build();

        LLMResponse llmResponse = promptSender.send(llmRequest);

        // Parse JSON response
        List<ValidationAssertion> assertions = parseAssertions(llmResponse.responseText());

        return new AssertionGenerationResult(request.rcaStatement(), prompt, assertions);
    }

    private List<ValidationAssertion> parseAssertions(String jsonContent) {
        try {
            // Clean up JSON if LLM added extra text
            String cleanJson = extractJson(jsonContent);

            JsonNode root = objectMapper.readTree(cleanJson);
            JsonNode assertionsNode = root.get("assertions");

            List<ValidationAssertion> assertions = new ArrayList<>();
            for (JsonNode assertionNode : assertionsNode) {
                String claim = assertionNode.get("claim").asText();
                String validationQuestion = assertionNode.get("validationQuestion").asText();
                String supportingDesc = assertionNode.get("supportingEvidenceDescription").asText();
                String contradictingDesc = assertionNode.get("contradictingEvidenceDescription").asText();

                List<String> keywords = new ArrayList<>();
                JsonNode keywordsNode = assertionNode.get("searchKeywords");
                if (keywordsNode != null && keywordsNode.isArray()) {
                    for (JsonNode keyword : keywordsNode) {
                        keywords.add(keyword.asText());
                    }
                }

                assertions.add(new ValidationAssertion(
                    claim,
                    validationQuestion,
                    supportingDesc,
                    contradictingDesc,
                    keywords
                ));
            }

            return assertions;

        } catch (Exception e) {
            throw new RuntimeException("Failed to parse LLM assertion response: " + e.getMessage(), e);
        }
    }

    /**
     * Extracts JSON from LLM response that may contain extra text.
     */
    private String extractJson(String content) {
        // Find first { and last }
        int start = content.indexOf('{');
        int end = content.lastIndexOf('}');

        if (start >= 0 && end > start) {
            return content.substring(start, end + 1);
        }

        return content;
    }
}

// Made with Bob
