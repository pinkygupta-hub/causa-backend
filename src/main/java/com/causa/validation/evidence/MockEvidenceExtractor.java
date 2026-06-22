package com.causa.validation.evidence;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.causa.validation.shared.EvidenceCitation;
import com.causa.validation.shared.MockValidationContext;
import com.causa.validation.shared.ValidationDataSourceType;

import jakarta.enterprise.context.ApplicationScoped;

/**
 * Deterministic Phase 2 evidence extractor.
 *
 * <p>Uses simple keyword matching now while preserving the same contract
 * expected by a future LLM-backed implementation.
 *
 * @since 0.0.1
 */
@ApplicationScoped
public class MockEvidenceExtractor implements EvidenceExtractor {

    @Override
    public EvidenceExtractionResult extractEvidence(EvidenceExtractionRequest request) {
        String prompt = EvidenceExtractionPromptTemplate.render(request);
        
        List<EvidenceCitation> supporting = new ArrayList<>();
        List<EvidenceCitation> contradicting = new ArrayList<>();
        String missingNote = null;

        MockValidationContext context = request.validationContext();
        List<String> keywords = request.assertion().searchKeywords();

        extractFromSource(context.podLogs(), ValidationDataSourceType.POD_LOG, "pod-logs",
            keywords, supporting, contradicting);

        extractFromSource(context.kubernetesEvents(), ValidationDataSourceType.KUBERNETES_EVENT, "kubernetes-events",
            keywords, supporting, contradicting);

        extractFromSource(context.resourceUsage(), ValidationDataSourceType.RESOURCE_USAGE, "resource-usage",
            keywords, supporting, contradicting);

        extractFromSource(context.kruizeRecommendations(), ValidationDataSourceType.KRUIZE_RECOMMENDATION, "kruize-recommendations",
            keywords, supporting, contradicting);

        if (supporting.isEmpty() && contradicting.isEmpty()) {
            missingNote = "MISSING: No evidence found in pod logs, Kubernetes events, resource usage, or Kruize recommendations for assertion: "
                + request.assertion().claim();
        }

        return new EvidenceExtractionResult(
            request.assertion().claim(),
            prompt,
            supporting,
            contradicting,
            missingNote
        );
    }

    private void extractFromSource(List<String> sourceLines, ValidationDataSourceType sourceType, 
                                  String sourceName, List<String> keywords,
                                  List<EvidenceCitation> supporting, List<EvidenceCitation> contradicting) {
        
        for (int i = 0; i < sourceLines.size(); i++) {
            String line = sourceLines.get(i);
            String lowerLine = line.toLowerCase(Locale.ROOT);
            
            for (String keyword : keywords) {
                String lowerKeyword = keyword.toLowerCase(Locale.ROOT);
                
                if (lowerLine.contains(lowerKeyword)) {
                    String timestamp = extractTimestamp(line);
                    EvidenceCitation citation = new EvidenceCitation(
                        sourceType,
                        sourceName,
                        timestamp,
                        line.trim()
                    );
                    
                    // Simple heuristic: lines with "failed", "error", "killed" are supporting
                    // Lines with "success", "ready", "ok" might be contradicting
                    if (isSupporting(lowerLine, lowerKeyword)) {
                        supporting.add(citation);
                    } else if (isContradicting(lowerLine, lowerKeyword)) {
                        contradicting.add(citation);
                    } else {
                        supporting.add(citation); // Default to supporting
                    }
                    break; // Only match first keyword per line
                }
            }
        }
    }

    private boolean isSupporting(String lowerLine, String lowerKeyword) {
        return lowerLine.contains("failed") || lowerLine.contains("error") || 
               lowerLine.contains("killed") || lowerLine.contains("oom") ||
               lowerLine.contains("not ready") || lowerLine.contains("missing");
    }

    private boolean isContradicting(String lowerLine, String lowerKeyword) {
        return lowerLine.contains("success") || lowerLine.contains("ready") || 
               lowerLine.contains("ok") || lowerLine.contains("healthy") ||
               lowerLine.contains("started") || lowerLine.contains("initialized");
    }

    private String extractTimestamp(String line) {
        // Simple timestamp extraction - look for ISO format or common log patterns
        if (line.matches(".*\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}.*")) {
            int start = line.indexOf("20"); // Assume 20xx year
            if (start >= 0) {
                int end = Math.min(start + 19, line.length()); // ISO timestamp length
                return line.substring(start, end);
            }
        }
        return "unknown";
    }
}

// Made with Bob
