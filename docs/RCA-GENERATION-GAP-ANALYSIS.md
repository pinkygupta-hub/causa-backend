# Bob Shell Integration - Gap Analysis & Findings

**Date:** 2026-06-25  
**Branch:** `rca-llm-integration`  
**Status:** ⚠️ Partial Success - Installation Complete, Output Format Issue  

## Executive Summary

Bob Shell CLI has been successfully installed and integrated into causa-backend. The CLI executes properly and communicates with IBM's Bob AI service. However, **Bob's LLM does not return pure JSON output as required** for RCA generation, despite explicit instructions in both system and user prompts.

## What Works ✅

### 1. Installation & Deployment
- **Bob Shell Installation**: Successfully installed via IBM's official script (`https://bob.ibm.com/download/bobshell.sh`)
- **Container Integration**: Bob Shell runs in OpenShift containers with proper permissions
- **Node.js Dependencies**: Node.js v22.x installed and configured
- **Permission Handling**: Non-root user (185) has write access to `/home/default/.bob`
- **API Authentication**: Bob Shell API key configured via `BOBSHELL_API_KEY` environment variable

### 2. Runtime Execution
- **Health Checks**: Bob Shell connectivity verified on startup
- **Process Execution**: `ProcessBuilder` successfully spawns and manages Bob Shell CLI
- **Stdin Mode**: Large prompts (>100KB) handled via stdin without issues
- **Exit Codes**: Proper exit code handling (0 = success)
- **Output Parsing**: Successfully extracts content between `---output---` markers

### 3. Code Integration
- **BobShellPromptSender**: Implements `PromptSender` interface correctly
- **Conditional Activation**: `@IfBuildProperty` works with build-time flag `-Dcausa.llm.provider=bob-shell`
- **Prompt Building**: System prompt + user prompt correctly combined
- **Token Extraction**: Parses Bob's stats JSON for token usage
- **Template Loading**: Correctly loads `ibm-bob` template from YAML

## What Doesn't Work ❌

### Critical Issue: JSON Output Format

**Problem:** Bob's LLM returns conversational text or tool outputs instead of pure JSON, despite explicit instructions.

**Evidence from Testing:**

#### Test 1: Initial RCA Generation
```
DEBUG: Extracted content between markers (first 1000 chars) | content="I understand you want me to perform a Root Cause Analysis for Kubernetes pod memory issues. However, I cannot proceed without the actual data signals to analyze.
ERROR: JsonParseException: Unrecognized token 'I': was expecting (JSON String, Number, Array, Object or token 'null', 'true' or 'false')
```

**Analysis:** Bob started with conversational preamble "I understand..." instead of JSON object.

#### Test 2: With Forceful JSON Instructions
After adding multiple JSON-only instructions to system and user prompts:
```
DEBUG: Split output into parts | partsCount=5
DEBUG: Extracted content between markers (first 1000 chars) | content="Listed 5 item(s)."
ERROR: JsonParseException: Unrecognized token 'Listed': was expecting (JSON String, Number, Array, Object or token 'null', 'true' or 'false')
```

**Analysis:** Bob invoked a tool (listing function) instead of returning JSON. The `partsCount=5` suggests multiple tool calls were made.

#### Test 3: Health Check (Baseline)
```
DEBUG: Split output into parts | partsCount=3
DEBUG: Extracted content between markers (first 1000 chars) | content="OK"
```

**Analysis:** Simple prompts ("Respond with OK") work correctly, but complex RCA prompts trigger conversational or tool-use behavior.

## Root Cause Analysis

### Why Bob Shell Doesn't Return JSON

1. **YOLO Mode Behavior**: Bob Shell runs with `--yolo` flag, which auto-approves all tool calls. This encourages tool use over direct responses.

2. **LLM Training**: The underlying LLM (likely Granite or IBM BAM) appears trained for conversational interaction, not structured output generation.

3. **CLI Design**: The `-o json` flag only controls **Bob Shell's output format** (markers and stats), not what the **LLM** returns between the markers.

4. **Prompt Adherence**: Even with forceful instructions like:
   - "CRITICAL: Your response must be ONLY a JSON object"
   - "Do not write any conversational text"
   - "The very first character must be '{'"
   
   The LLM still produces conversational text or tool invocations.

### Output Format Structure

Bob Shell's actual output format:
```
YOLO mode is enabled. All tool calls will be automatically approved.
---output---
{LLM response - MAY NOT BE JSON}
---output---
{
  "response": "",
  "stats": {
    "models": {
      "premium": {
        "tokens": {
          "prompt": 13198,
          "candidates": 91,
          "total": 13289
        }
      }
    }
  }
}
```

The section between markers can be:
- ✅ Simple text ("OK") for basic prompts
- ❌ Conversational text ("I understand...")
- ❌ Tool output ("Listed 5 item(s).")
- ❓ JSON (not yet observed in testing)

## What We Tried

### Attempt 1: Basic Integration
- Used `-o json` flag
- Expected Bob to respect prompt instructions
- **Result:** Conversational text returned

### Attempt 2: Template Mismatch Fix
- Fixed YAML template key from `bob:` to `ibm-bob:`
- Ensured `ModelType.from()` correctly maps to template
- **Result:** Template loaded correctly, but output still not JSON

### Attempt 3: Forceful Prompt Instructions
- Added JSON-only instruction to system prompt
- Replaced user prompt OUTPUT_REQUIREMENT with stronger language
- Used phrases like "CRITICAL", "MUST", "will cause parsing error"
- **Result:** Bob invoked tools instead of returning JSON

### Attempt 4: Extended Debug Logging
- Increased output capture from 500 to 2000 chars
- Logged partsCount to understand tool invocations
- **Result:** Confirmed tool usage and conversational responses

## Technical Debt Created

### New Code Added
1. **BobShellPromptSender.java** - 400+ lines
2. **LLMConstants.BobShell** - Constants class
3. **ModelType.BOB** - Enum entry
4. **Dockerfile.jvm** - Node.js + Bob Shell installation (15 lines)
5. **Prompt Template** - `ibm-bob:` section in YAML
6. **Debug Logging** - WARN-level debug statements (should be removed)

### Build Configuration
- Build property: `-Dcausa.llm.provider=bob-shell` required
- Cannot switch providers at runtime (Quarkus `@IfBuildProperty` limitation)

### Deployment Artifacts
- Docker image: `quay.io/pingupta/irb:bob-forceful-json`
- ConfigMap: `LLM_PROVIDER: bob-shell`, `LLM_MODEL_NAME: bob-shell-1.0.4`
- Secret: Bob API key in `causa-llm-secrets`

## Comparison: Bob Shell vs Direct Anthropic

| Aspect | Bob Shell | Anthropic (Claude) |
|--------|-----------|-------------------|
| Installation | Requires Node.js 22+ + Bob CLI | API-only, no installation |
| Output Format | Conversational/tool-based | Structured JSON with prompt caching |
| JSON Adherence | ❌ Ignores instructions | ✅ Follows instructions reliably |
| Token Tracking | ✅ Extracted from stats | ✅ Native response metadata |
| Prompt Caching | Unknown | ✅ Supported |
| Latency | ~25-35 seconds | ~10-20 seconds |
| Container Size | +200MB (Node.js + Bob) | No overhead |
| Error Handling | Process management complexity | Simple HTTP errors |

## Possible Solutions

### Option 1: Post-Process Bob's Output ⚠️ Risky
**Approach:** Extract JSON from conversational text using regex or another LLM call.

**Pros:**
- Might handle cases where JSON is embedded in text
- Could work if Bob sometimes returns JSON

**Cons:**
- Unreliable - what if no JSON is present?
- Performance overhead (extra parsing or LLM call)
- Fragile - breaks if Bob's response format changes
- Doesn't solve tool invocation issue

**Recommendation:** ❌ Not recommended

### Option 2: Switch to IBM BAM API ✅ Recommended
**Approach:** Use IBM's Business Automation Manager (BAM) REST API directly instead of Bob Shell CLI.

**Pros:**
- Direct API control over output format
- May support structured output or JSON mode
- No CLI dependency, simpler container
- Better error handling
- Likely faster (no CLI overhead)

**Cons:**
- Requires API documentation from IBM
- Different authentication mechanism
- May need code refactor

**Recommendation:** ✅ **Pursue this if staying with IBM LLM**

### Option 3: Contact IBM Support 🔍 Investigate
**Approach:** Ask Rashmi/IBM team if Bob Shell has a JSON-only mode or structured output flag.

**Questions to Ask:**
1. Does Bob Shell support a `--format json` or `--structured-output` flag?
2. Is there a way to disable tool use / force direct responses?
3. Can we configure Bob to return only the LLM response without preamble?
4. Is there a different IBM API (e.g., BAM REST API) better suited for structured output?

**Recommendation:** ✅ **Do this immediately** - may be a simple flag we're missing

### Option 4: Keep Vertex AI/Anthropic ✅ Safest
**Approach:** Abandon Bob Shell, continue with Claude via Vertex AI.

**Pros:**
- Already working perfectly
- Reliable JSON output
- Prompt caching support
- Proven in production
- Better documentation

**Cons:**
- Doesn't explore IBM alternatives
- May have cost implications
- Loses potential IBM-specific features

**Recommendation:** ✅ **Fallback if Options 2 & 3 fail**

## Next Steps

### Immediate Actions (Priority Order)

1. **Contact IBM/Rashmi** 📧
   - Ask about JSON-only output mode
   - Request IBM BAM API documentation
   - Inquire about structured output support
   - Share error logs showing tool invocation

2. **Document Current State** 📝
   - ✅ This gap analysis
   - Commit working Bob Shell integration code (for reference)
   - Tag as `bob-shell-investigation`

3. **Decision Point** ⚠️
   - If IBM confirms JSON mode exists → Retry with correct flags
   - If IBM recommends BAM API → Implement API client
   - If no solution → Remove Bob Shell integration, keep Vertex AI

### Code Cleanup Required

If Bob Shell integration is abandoned:
- Remove `BobShellPromptSender.java`
- Remove Bob Shell constants from `LLMConstants`
- Remove `ModelType.BOB`
- Remove Node.js + Bob Shell from `Dockerfile.jvm`
- Remove `ibm-bob:` template section
- Remove build property from Maven commands
- Revert ConfigMap to `vertex-ai-anthropic`

If Bob Shell integration is kept:
- Remove debug WARN logging (change to DEBUG or INFO)
- Add proper error messages for JSON parse failures
- Implement retry logic or fallback
- Document limitations in user-facing docs

## Files Modified in This Branch

### New Files
- `src/main/java/com/causa/llm/BobShellPromptSender.java` (new)
- `docs/RCA-GENERATION-GAP-ANALYSIS.md` (this file)
- `docs/architecture/rca-generation-flow.md` (new)
- `src/main/java/com/causa/core/domain/RootCauseAnalysis.java` (new)
- `src/main/java/com/causa/core/services/RcaPromptBuilder.java` (new)

### Modified Files
- `src/main/java/com/causa/mcp/McpContextCollector.java`
- `src/main/java/com/causa/common/constants/LLMConstants.java`
- `src/main/java/com/causa/common/constants/ModelType.java`
- `src/main/resources/prompts/rca-prompt-template.yml`
- `src/main/docker/Dockerfile.jvm`
- `docs/llm/bob-shell-integration.md` (updated by Aakriti)

## Deployment Configuration

### Current OpenShift Setup
```yaml
# ConfigMap (causa-config)
LLM_PROVIDER: bob-shell
LLM_MODEL_NAME: bob-shell-1.0.4

# Secret (causa-llm-secrets)
BOBSHELL_API_KEY: <redacted>

# Image
quay.io/pingupta/irb:bob-forceful-json
```

### Maven Build Command
```bash
mvn clean package -Dcausa.llm.provider=bob-shell -DskipTests
```

## Lessons Learned

1. **CLI vs API**: CLI tools designed for human interaction may not suit programmatic JSON output needs.

2. **YOLO Mode**: Auto-approving tool calls encourages tool use over direct responses, incompatible with structured output.

3. **Build-Time Properties**: Quarkus `@IfBuildProperty` prevents runtime provider switching - consider factory pattern for production.

4. **LLM Prompt Adherence**: Not all LLMs follow structural output instructions equally well. Claude/GPT-4 excel at this; others may not.

5. **Testing Earlier**: Should have tested JSON output with simple RCA before full integration.

## References

- **IBM Bob Shell**: https://bob.ibm.com/download/bobshell.sh
- **PR #24**: Aakriti's Bob Shell integration PR
- **Bob Shell Docs**: docs/llm/bob-shell-integration.md
- **RCA Prompt Template**: src/main/resources/prompts/rca-prompt-template.yml
- **Test Logs**: See pod logs from `causa-backend-*` pods in `pinky` namespace

## Appendix: Test Results Summary

| Test | Alert Name | Bob Response | Error |
|------|-----------|--------------|-------|
| Health Check | (Startup) | "OK" | None ✅ |
| RCA Test 1 | BobDebugTest | "I understand you want me to..." | JsonParseException (token 'I') ❌ |
| RCA Test 2 | BobFinalTest | "Listed 5 item(s)." | JsonParseException (token 'Listed') ❌ |
| RCA Test 3 | BobForcefulJson | "Listed 5 item(s)." | JsonParseException ❌ |

**Pattern:** Simple prompts work. Complex RCA prompts trigger conversational/tool behavior.

## Contact

For questions about this analysis:
- **Branch**: `rca-llm-integration`
- **Integration Owner**: Aakriti Gulati (original Bob Shell PR)
- **IBM Contact**: Rashmi (Bob API key provider)
- **Investigation**: Pinky Gupta (gap analysis & debugging)

---

**Conclusion:** Bob Shell integration is technically successful but functionally incompatible with RCA generation due to JSON output format issues. Recommend contacting IBM for structured output support or switching to IBM BAM REST API.
