package com.causa.validation.shared;

import java.util.List;

/**
 * Mock validation context used for deterministic evidence extraction flows.
 *
 * @param podLogs available pod log lines
 * @param kubernetesEvents available Kubernetes event lines
 * @param resourceUsage available resource usage snapshot lines
 * @param kruizeRecommendations available Kruize recommendation lines
 * @since 0.0.1
 */
public record MockValidationContext(
    List<String> podLogs,
    List<String> kubernetesEvents,
    List<String> resourceUsage,
    List<String> kruizeRecommendations
) {

    public MockValidationContext {
        podLogs = podLogs == null ? List.of() : List.copyOf(podLogs);
        kubernetesEvents = kubernetesEvents == null ? List.of() : List.copyOf(kubernetesEvents);
        resourceUsage = resourceUsage == null ? List.of() : List.copyOf(resourceUsage);
        kruizeRecommendations = kruizeRecommendations == null ? List.of() : List.copyOf(kruizeRecommendations);
    }

    public static MockValidationContext empty() {
        return new MockValidationContext(List.of(), List.of(), List.of(), List.of());
    }
}
