# RCA String Context and YAML-Based Prompts Implementation

## Overview
Implemented a flexible, model-agnostic RCA system that:
1. **Handles context as strings** (not Alert objects)
2. **Uses YAML-based prompt templates** for model-specific customization
3. **Integrates with existing LangChain infrastructure** including Bob shell support

## Key Changes

### 1. Simplified MCP Context Collection

**File:** `McpContextCollector.java`

- Removed detailed MCP implementation from `*AsString` methods
- Added placeholder `TODO` comments for actual MCP integration
- Methods now return formatted placeholders instead of implementing full MCP calls
- `collectContextAsString(Alert)` builds a structured string with sections:
  - POD_STATUS
  - POD_EVENTS
  - APPLICATION_LOGS
  - MISSING_SIGNALS (documents what's not integrated yet)

**Why:** The actual MCP integration will be done separately. For now, we assume string context will come from somewhere.

### 2. YAML-Based Prompt Templates

**File:** `src/main/resources/prompts/rca-prompt-template.yml`

Created a YAML configuration file with model-specific prompts:

```yaml
default:  # For Claude, GPT, etc.
  name: "rca-memory-analysis"
  system_prompt: "You are an expert RCA engine..."
  user_prompt: "Full detailed prompt with {{placeholders}}..."

bob:  # For IBM BAM Bob/Granite models
  name: "rca-memory-analysis-bob"
  system_prompt: "Concise system prompt for Bob..."
  user_prompt: "Shorter, Bob-optimized prompt..."

ollama:  # For local Ollama models
  name: "rca-memory-analysis-ollama"
  system_prompt: "Minimal system prompt..."
  user_prompt: "Compact prompt for local models..."
```

**Placeholders supported:**
- `{{alertName}}`
- `{{severity}}`
- `{{podName}}`
- `{{namespace}}`
- `{{containerName}}`
- `{{context}}`

### 3. Prompt Template Loader

**File:** `PromptTemplateLoader.java` (NEW)

- ApplicationScoped service that loads and caches YAML templates
- `loadTemplate(String modelType)`: Returns template for specified model
- `PromptTemplate.render()`: Replaces placeholders with actual values
- Caches templates in memory for performance
- Fallback to "default" template if model-specific not found

### 4. Updated RCA Prompt Builder

**File:** `RcaPromptBuilder.java`

**Before:**
- Hardcoded 250+ line prompt template as Java String
- Used String.format() for substitution
- No model-specific variations

**After:**
- Injects `PromptTemplateLoader` and `LLMConfig`
- `buildPrompt()` now:
  1. Determines model type from config
  2. Loads appropriate YAML template
  3. Renders with alert data
- `getSystemPrompt()`: Returns model-specific system prompt
- `determineModelType()`: Maps provider/model to template type
  - Bob/Granite → "bob"
  - Ollama → "ollama"
  - Others → "default"
- Kept legacy method as `@Deprecated` for backward compatibility

### 5. Updated Diagnostic Service

**File:** `DiagnosticServiceImpl.java`

- Injected `RcaPromptBuilder` in constructor
- `collectContext()` now returns `String` instead of `void`
- Calls `mcpContextCollector.collectContextAsString(alert)`
- `performRootCauseAnalysis()` signature changed:
  - Takes `String contextString` parameter
  - Calls `rcaPromptBuilder.buildPrompt(alert, contextString)`
  - Returns the generated prompt
- `triggerDiagnostics()` wires the flow:
  - Captures context string
  - Passes to RCA prompt builder
  - Logs prompt details

### 6. Added SnakeYAML Dependency

**File:** `pom.xml`

```xml
<dependency>
    <groupId>org.yaml</groupId>
    <artifactId>snakeyaml</artifactId>
    <version>2.2</version>
</dependency>
```

## Bob Shell Integration (from PR #24)

Based on PR #24 review, the following Bob shell features are already implemented:

### Existing Bob Integration
- `BobShellPromptSender.java`: Direct CLI execution via ProcessBuilder
- Conditional bean activation: `@IfBuildProperty(name="causa.llm.provider", stringValue="bob-shell")`
- Large prompt handling: >100KB via stdin
- Configuration in `application.yml` under `causa.llm.bob`
- Docker support with Node.js 20 and Bob Shell CLI v1.0.4

### Bob-Specific Prompt Template
The YAML template system now includes a **bob-specific prompt variant** that:
- Uses concise language (Bob models prefer shorter prompts)
- Focuses on structured output requirements
- Provides category definitions inline
- Optimized for the Bob/Granite model characteristics

### How Bob Template is Selected
```java
private String determineModelType(String provider, String modelName) {
    if (modelName != null && (modelName.toLowerCase().contains("bob") ||
        modelName.toLowerCase().contains("granite"))) {
        return "bob";  // Uses bob section from YAML
    }
    // ...
}
```

## Data Flow

```
Alert (with pod details)
    ↓
DiagnosticServiceImpl.triggerDiagnostics()
    ↓
collectContext() → String (MCP context - placeholder for now)
    ↓
performRootCauseAnalysis()
    ↓
RcaPromptBuilder.buildPrompt()
    ├─ Determine model type (bob/ollama/default)
    ├─ Load YAML template
    └─ Render with alert data
    ↓
Complete prompt string (ready for LLM)
    ↓
[TODO: Call LLM via LangChainPromptSender or BobShellPromptSender]
    ↓
[TODO: Parse JSON response to RootCauseAnalysis object]
```

## Configuration

### For Claude/Anthropic (uses "default" template):
```yaml
causa:
  llm:
    provider: anthropic
    model-name: claude-sonnet-4-6
```

### For Bob Shell (uses "bob" template):
```yaml
causa:
  llm:
    provider: bob-shell
    model-name: granite-3.1-2b-instruct
    bob:
      shell-path: bob
      api-key: ${BOBSHELL_API_KEY}
```

### For Ollama (uses "ollama" template):
```yaml
causa:
  llm:
    provider: ollama
    model-name: llama3
```

## Next Steps

### Immediate (To Complete RCA Flow)
1. **Implement actual LLM call** in `DiagnosticServiceImpl`:
   - Build `LLMRequest` with system + user prompts
   - Call `promptSender.send(request)` (works with both LangChain and BobShell)
   - Handle `LLMResponse`

2. **Parse LLM JSON response**:
   - Extract JSON from response text
   - Deserialize to `RootCauseAnalysis` object
   - Handle parsing errors gracefully

3. **Store RCA result**:
   - Persist to database
   - Update diagnostic status
   - Return to API caller

### Future Enhancements
1. **Add more model-specific templates** (GPT-4, Llama, Mistral, etc.)
2. **Implement actual MCP context collection** (replace placeholders)
3. **Add versioning** to prompt templates (A/B testing)
4. **Metrics**: Track which templates perform best for which models
5. **Validation**: Add schema validation for YAML templates on startup

## Testing

### Unit Tests Needed
- `PromptTemplateLoaderTest`: Test YAML loading, caching, fallback
- `RcaPromptBuilderTest`: Test model type detection, template rendering
- `DiagnosticServiceImplTest`: Test string context flow

### Integration Tests Needed
- End-to-end RCA generation with mocked LLM
- Test each model variant (default, bob, ollama)
- Verify JSON output matches `RootCauseAnalysis` schema

## Benefits

1. **Model Flexibility**: Easy to optimize prompts per model without code changes
2. **Maintainability**: Prompts in YAML are easier to read and update than 250-line Java strings
3. **Versioning**: Can version prompts independently from code
4. **A/B Testing**: Can test different prompt variations by editing YAML
5. **Bob Support**: Optimized prompt for IBM's Bob/Granite models
6. **String-Based**: Context is treated as opaque strings, no coupling to Alert structure
7. **Clean Separation**: Prompt engineering is separate from business logic
