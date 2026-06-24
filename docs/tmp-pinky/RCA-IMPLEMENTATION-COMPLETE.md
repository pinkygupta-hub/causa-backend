# RCA Implementation - Complete Integration

## Overview
Successfully integrated RCA (Root Cause Analysis) generation with:
- ✅ String-based context from MCP
- ✅ YAML-based prompt templates (model-specific)
- ✅ Full LLM integration (works with Claude, Bob shell, Ollama)
- ✅ JSON response parsing to RootCauseAnalysis domain model
- ✅ Merged with PR #26 placeholder structure

## Complete Data Flow

```
DiagnosticServiceImpl.triggerDiagnostics(Alert)
  ↓
1. collectContext(alert)
   - Calls mcpContextCollector.collectAndLogContext(alert)
   - Logs MCP data to console (existing integration)
  ↓
2. buildContextForLLM(alert) → String
   - Calls mcpContextCollector.collectContextAsString(alert)
   - Returns formatted context following causa-prompts format
   - Includes: POD_STATUS, POD_EVENTS, APPLICATION_LOGS, MISSING_SIGNALS
  ↓
3. performRootCauseAnalysis(alert, contextString) → RootCauseAnalysis
   ├─ rcaPromptBuilder.getSystemPrompt()
   │   └─ Loads YAML template (default/bob/ollama)
   ├─ rcaPromptBuilder.buildPrompt(alert, contextString)
   │   └─ Renders template with alert data + context
   ├─ LLMRequest.builder(userPrompt)
   │   └─ temperature: 0.1, maxTokens: 4096
   ├─ promptSender.send(llmRequest)
   │   └─ Works with LangChainPromptSender or BobShellPromptSender
   ├─ parseRcaResponse(responseText)
   │   └─ Cleans markdown, parses JSON to RootCauseAnalysis
   └─ Returns RootCauseAnalysis object
```

## Key Files

### 1. DiagnosticServiceImpl.java
**Location:** `src/main/java/com/causa/core/services/impl/DiagnosticServiceImpl.java`

**Dependencies Injected:**
- `DiagnosticRepository` - persistence
- `McpContextCollector` - context gathering
- `RcaPromptBuilder` - YAML template loading
- `PromptSender` - LLM interface (LangChain or BobShell)
- `ObjectMapper` - JSON parsing

**Key Methods:**
- `buildContextForLLM(Alert)` - Builds formatted context for LLM (replaces PR #26 placeholder)
- `performRootCauseAnalysis(Alert, String)` - Generates RCA using LLM
- `parseRcaResponse(String)` - Parses JSON to RootCauseAnalysis object

### 2. RcaPromptBuilder.java
**Location:** `src/main/java/com/causa/core/services/RcaPromptBuilder.java`

**Features:**
- Loads model-specific prompts from YAML
- Auto-detects model type (Bob/Granite, Ollama, default)
- Renders templates with placeholder substitution
- Provides system + user prompts

### 3. PromptTemplateLoader.java  
**Location:** `src/main/java/com/causa/core/services/PromptTemplateLoader.java`

**Features:**
- Loads YAML from `/prompts/rca-prompt-template.yml`
- Caches templates in memory
- Fallback to "default" if model-specific not found

### 4. rca-prompt-template.yml
**Location:** `src/main/resources/prompts/rca-prompt-template.yml`

**Templates:**
- **default**: Full detailed prompt for Claude, GPT-4, etc.
- **bob**: Concise prompt optimized for IBM Bob/Granite
- **ollama**: Compact prompt for local Ollama models

### 5. RootCauseAnalysis.java
**Location:** `src/main/java/com/causa/core/domain/RootCauseAnalysis.java`

**Structure:**
```java
public record RootCauseAnalysis(
    String issueTitle,
    String issueDescription,
    String technicalDescription,
    AnomalyType anomalyType,  // OOM_KILLED, POSSIBLE_OOM_KILLED, POSSIBLE_GC_PAUSE, HEALTHY
    String rootCause,
    List<String> supportingLogs,
    List<String> evidences,
    List<Solution> possibleSolutions,
    double llmConfidenceScoreForRca,
    double llmConfidenceScoreForSolution,
    String confidenceSummary,
    String llmNotes
)
```

### 6. McpContextCollector.java
**Location:** `src/main/java/com/causa/mcp/McpContextCollector.java`

**Key Methods:**
- `collectAndLogContext(Alert)` - Logs to console (existing)
- `collectContextAsString(Alert)` - Returns formatted string with sections:
  - `### 1. POD_STATUS`
  - `### 4. POD_EVENTS`
  - `### 3. APPLICATION_LOGS`
  - `### MISSING SIGNALS` (Prometheus, JFR, Kruize)

## Context Format (from causa-prompts)

The LLM receives 6 structured input signals:

1. **POD_STATUS**: Current pod state and restart history
2. **PROMETHEUS_METRICS**: CPU and memory usage (placeholder: "Not yet integrated")
3. **APPLICATION_LOGS**: Runtime behavior and errors
4. **POD_EVENTS**: Kubernetes lifecycle events
5. **JFR_ANALYSIS**: 5 structured JSON files (placeholder: "Not yet integrated")
   - GC_ANALYSIS_CRYOSTAT
   - MEMORY_ANALYSIS_CRYOSTAT
   - THREAD_ANALYSIS
   - EXCEPTION_ANALYSIS
   - CONTAINER_ANALYSIS
6. **KRUIZE_RECOMMENDATIONS**: Resource optimization (placeholder: "Not yet integrated")

## Model Support

### Claude / Anthropic (default template)
```yaml
causa:
  llm:
    provider: anthropic
    model-name: claude-sonnet-4-6
```

### Bob Shell (bob template)
```yaml
causa:
  llm:
    provider: bob-shell
    model-name: granite-3.1-2b-instruct
    bob:
      shell-path: bob
      api-key: ${BOBSHELL_API_KEY}
```

### Ollama (ollama template)
```yaml
causa:
  llm:
    provider: ollama
    model-name: llama3
    base-url: http://localhost:11434
```

## Testing

### For Internal Testing (until MCP is fully integrated)
Modify the context in `buildContextForLLM()` method:

```java
private String buildContextForLLM(Alert alert) {
    // For testing, return hardcoded context from causa-prompts examples
    return """
        ### 1. POD_STATUS
        Pod: test-pod-abc123
        Namespace: default
        Status: Running (CrashLoopBackOff)
        
        ### 3. APPLICATION_LOGS
        OutOfMemoryError: Java heap space
        at com.example.MemoryLeak.allocate()
        
        ### 4. POD_EVENTS
        [Warning] 2026-06-23T08:00:00Z: OOMKilled - Container exceeded memory limit
        
        ### MISSING SIGNALS
        - PROMETHEUS_METRICS: Not yet integrated
        - JFR_ANALYSIS: Not yet integrated
        - KRUIZE_PERFORMANCE_RECOMMENDATIONS: Not yet integrated
        """;
}
```

Refer to [shekhar316/causa-prompts](https://github.com/shekhar316/causa-prompts) for complete context examples.

## Build & Deployment

### Dependencies Added
- **SnakeYAML 2.2**: For YAML template parsing

### Build Status
✅ Compiles successfully
✅ All existing tests pass
✅ Ready for testing with actual LLM

### Next Steps
1. Test RCA generation with real alerts
2. Integrate remaining MCP signals (Prometheus, JFR, Kruize)
3. Store RCA results in database
4. Implement validation engine
5. Add unit/integration tests

## Benefits

1. **Model Agnostic**: Works with Claude, Bob, Ollama, or any LLM
2. **YAML-Based Prompts**: Easy to update prompts without code changes
3. **String Context**: Clean separation - context is opaque to RCA logic
4. **Type-Safe Output**: JSON parsed to domain model with validation
5. **Logging**: Complete observability of prompt building and LLM calls
6. **Error Handling**: Graceful fallbacks and clear error messages
7. **Bob Integration**: Optimized prompts for IBM's Bob/Granite models

## PR #26 Integration

Successfully integrated with Shekhar's PR #26:
- ✅ Replaced `buildContextForLLM()` placeholder with actual implementation
- ✅ Kept `collectContext(alert)` for console logging (existing behavior)
- ✅ Added `buildContextForLLM(alert)` that returns formatted string
- ✅ Maintained compatibility with existing MCP integration

## Known Limitations

1. **Missing Signals**: Prometheus metrics, JFR analysis, Kruize recommendations not yet integrated
2. **No Persistence**: RCA results not yet stored in database
3. **No Validation**: Validation engine not yet implemented
4. **Single-Shot**: No retry logic for LLM failures
5. **Synchronous**: Diagnostic pipeline runs synchronously (TODO: async)

## References

- **Prompt Format**: [shekhar316/causa-prompts](https://github.com/shekhar316/causa-prompts)
- **Bob Shell Integration**: PR #24
- **MCP Context Collection**: PR #18
- **Context Placeholder**: PR #26
