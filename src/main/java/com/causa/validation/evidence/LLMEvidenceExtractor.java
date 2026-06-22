package com.causa.validation.evidence;

import com.causa.core.domain.LLMRequest;
import com.causa.core.domain.LLMResponse;
import com.causa.core.ports.llm.PromptSender;
import com.causa.validation.shared.EvidenceCitation;
import com.causa.validation.shared.ValidationDataSourceType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.List;

/**
 * LLM-powered Phase 2 evidence extractor.
 *
 * <p>Uses Bob to extract exact evidence citations from logs/events/metrics.
 *
 * @since 0.0.1
 */
@ApplicationScoped
public class LLMEvidenceExtractor implements EvidenceExtractor {

    private final PromptSender promptSender;
    private final ObjectMapper objectMapper;

    private static final String SYSTEM_PROMPT = """
        You are a precise evidence citation extractor.

        STRICT RULES:
        - Only cite EXACT log lines/events from the provided data
        - Include full timestamp and source name with each citation
        - Separate supporting vs contradicting evidence
        - If no evidence found, explicitly state in missingNote
        - DO NOT infer or invent evidence not present in the data
        - Quote the complete line, not summaries

        Return ONLY valid JSON:
        {
          "supportingEvidence": [
            {
              "sourceType": "POD_LOG",
              "sourceName": "pod-logs",
              "timestamp": "2026-06-18T06:09:04",
              "excerpt": "exact log line here"
            }
          ],
          "contradictingEvidence": [],
          "missingNote": "MISSING: description or null"
        }

        Do NOT include any text before or after the JSON.
        """;

    @Inject
    public LLMEvidenceExtractor(PromptSender promptSender, ObjectMapper objectMapper) {
        this.promptSender = promptSender;
        this.objectMapper = objectMapper;
    }

    @Override
    public EvidenceExtractionResult extractEvidence(EvidenceExtractionRequest request) {
        String prompt = EvidenceExtractionPromptTemplate.render(request);

        // Call LLM (Bob as Evidence Searcher)
        LLMRequest llmRequest = LLMRequest.builder(prompt)
            .systemPrompt(SYSTEM_PROMPT)
            .temperature(0.2)  // Precise extraction
            .maxTokens(1500)
            .build();

        LLMResponse llmResponse = promptSender.send(llmRequest);

        // Parse response
        return parseEvidenceResult(llmResponse.responseText(), request.assertion().claim(), prompt);
    }

    private EvidenceExtractionResult parseEvidenceResult(String jsonContent, String assertionClaim, String prompt) {
        try {
            String cleanJson = extractJson(jsonContent);
            JsonNode root = objectMapper.readTree(cleanJson);

            List<EvidenceCitation> supporting = parseCitations(root.get("supportingEvidence"));
            List<EvidenceCitation> contradicting = parseCitations(root.get("contradictingEvidence"));

            String missingNote = null;
            if (root.has("missingNote") && !root.get("missingNote").isNull()) {
                missingNote = root.get("missingNote").asText();
            }

            return new EvidenceExtractionResult(
                assertionClaim,
                prompt,
                supporting,
                contradicting,
                missingNote
            );

        } catch (Exception e) {
            throw new RuntimeException("Failed to parse LLM evidence response: " + e.getMessage(), e);
        }
    }

    private List<EvidenceCitation> parseCitations(JsonNode citationsNode) {
        List<EvidenceCitation> citations = new ArrayList<>();

        if (citationsNode != null && citationsNode.isArray()) {
            for (JsonNode node : citationsNode) {
                ValidationDataSourceType sourceType = ValidationDataSourceType.valueOf(
                    node.get("sourceType").asText()
                );
                String sourceName = node.get("sourceName").asText();
                String timestamp = node.get("timestamp").asText();
                String excerpt = node.get("excerpt").asText();

                citations.add(new EvidenceCitation(sourceType, sourceName, timestamp, excerpt));
            }
        }

        return citations;
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
