package com.causa.core.services;

import com.causa.config.LLMConfig;
import com.causa.core.domain.Alert;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * RCA Prompt Builder
 *
 * <p>Builds the Root Cause Analysis prompt by injecting MCP context
 * (pod status, events, logs) into YAML-based prompt templates.
 *
 * <p>Supports model-specific prompt variations loaded from
 * {@code /prompts/rca-prompt-template.yml}.
 *
 * @since 0.0.1
 */
@ApplicationScoped
public class RcaPromptBuilder {

    /**
     * Enum representing supported LLM model types for template selection.
     */
    public enum ModelType {
        VERTEX_AI_ANTHROPIC("vertex-ai-anthropic"),
        DIRECT_ANTHROPIC("direct-anthropic"),
        BOB("bob");

        private final String templateName;

        ModelType(String templateName) {
            this.templateName = templateName;
        }

        public String getTemplateName() {
            return templateName;
        }
    }

    private final PromptTemplateLoader templateLoader;
    private final LLMConfig llmConfig;

    @Inject
    public RcaPromptBuilder(PromptTemplateLoader templateLoader, LLMConfig llmConfig) {
        this.templateLoader = templateLoader;
        this.llmConfig = llmConfig;
    }

    /**
     * Builds the complete RCA prompt with alert details and MCP context.
     *
     * <p>Loads model-specific prompt template from YAML and renders it with alert data.
     *
     * @param alert the alert to analyze
     * @param mcpContext the collected MCP context as a string
     * @return the complete RCA prompt
     */
    public String buildPrompt(Alert alert, String mcpContext) {
        // Determine model type for template selection
        ModelType modelType = determineModelType(llmConfig.provider(), llmConfig.modelName());

        // Load appropriate template
        PromptTemplateLoader.PromptTemplate template = templateLoader.loadTemplate(modelType.getTemplateName());

        // Render the prompt with alert details
        return template.render(
            alert.getAlertName(),
            alert.getSeverity(),
            alert.getPodName(),
            alert.getNamespace(),
            alert.getContainerName(),
            mcpContext
        );
    }

    /**
     * Gets the system prompt for the configured model.
     *
     * @return the system prompt
     */
    public String getSystemPrompt() {
        ModelType modelType = determineModelType(llmConfig.provider(), llmConfig.modelName());
        PromptTemplateLoader.PromptTemplate template = templateLoader.loadTemplate(modelType.getTemplateName());
        return template.systemPrompt();
    }

    /**
     * Determines the template model type based on provider and model name.
     *
     * @param provider  the LLM provider
     * @param modelName the model name
     * @return the ModelType enum
     */
    private ModelType determineModelType(String provider, String modelName) {
        // Check for Bob/Granite models (IBM BAM)
        if (modelName != null && (modelName.toLowerCase().contains("bob") ||
            modelName.toLowerCase().contains("granite"))) {
            return ModelType.BOB;
        }

        // Check provider type
        if ("vertex-ai-anthropic".equalsIgnoreCase(provider)) {
            return ModelType.VERTEX_AI_ANTHROPIC;
        }

        if ("anthropic".equalsIgnoreCase(provider)) {
            return ModelType.DIRECT_ANTHROPIC;
        }

        // Default to vertex-ai-anthropic
        return ModelType.VERTEX_AI_ANTHROPIC;
    }
}
