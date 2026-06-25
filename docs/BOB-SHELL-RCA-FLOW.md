# Bob Shell RCA Generation Flow

**Date:** 2026-06-25  
**Purpose:** Document the complete flow of RCA generation using Bob Shell integration

---

## Overview

This document explains how Causa Backend generates Root Cause Analysis (RCA) using IBM's Bob Shell CLI, from alert reception to RCA storage.

---

## Architecture Diagram

```
┌─────────────────────┐
│  Prometheus Alert   │
│   (Alertmanager)    │
└──────────┬──────────┘
           │
           │ HTTP POST /api/v1/webhooks/alerts
           ▼
┌─────────────────────────────────────────────────────┐
│             Causa Backend (OpenShift)               │
│                                                     │
│  ┌────────────────────────────────────────────┐   │
│  │  1. AlertWebhook                           │   │
│  │     - Receives alert JSON                   │   │
│  │     - Validates severity                    │   │
│  │     - Checks cooldown                       │   │
│  └──────────────┬─────────────────────────────┘   │
│                 │                                   │
│                 ▼                                   │
│  ┌────────────────────────────────────────────┐   │
│  │  2. AlertServiceImpl                       │   │
│  │     - Transforms to Alert entity           │   │
│  │     - Triggers diagnostic pipeline         │   │
│  └──────────────┬─────────────────────────────┘   │
│                 │                                   │
│                 ▼                                   │
│  ┌────────────────────────────────────────────┐   │
│  │  3. DiagnosticServiceImpl                  │   │
│  │     - Calls MCP Context Collector          │   │
│  │     - Builds LLM prompt                    │   │
│  │     - Calls PromptSender                   │   │
│  │     - Parses RCA response                  │   │
│  └──────────────┬─────────────────────────────┘   │
│                 │                                   │
│        ┌────────┴────────┐                         │
│        │                 │                         │
│        ▼                 ▼                         │
│  ┌──────────┐   ┌────────────────────┐           │
│  │    4a.   │   │        4b.         │           │
│  │   MCP    │   │  RcaPromptBuilder  │           │
│  │ Context  │   │  - Load template   │           │
│  │Collector │   │  - Inject context  │           │
│  │          │   │  - Build prompt    │           │
│  └────┬─────┘   └─────────┬──────────┘           │
│       │                   │                        │
│       │                   │                        │
│       │         ┌─────────┴────────┐              │
│       │         │                  │              │
│       │         ▼                  │              │
│       │  ┌──────────────────────┐ │              │
│       │  │  5. PromptSender     │ │              │
│       │  │  (BobShellPromptSender)│              │
│       │  │  - Execute Bob Shell │ │              │
│       │  │  - Parse response    │ │              │
│       │  └─────────┬────────────┘ │              │
│       │            │               │              │
│       │            ▼               │              │
│       │    ┌──────────────┐       │              │
│       │    │  Bob Shell   │       │              │
│       │    │  CLI Process │       │              │
│       │    │              │       │              │
│       │    │  bob --accept-license │            │
│       │    │      --yolo           │            │
│       │    │      -o json          │            │
│       │    │      < prompt.txt     │            │
│       │    └───────┬──────┘        │            │
│       │            │                │            │
│       │            ▼                │            │
│       │    ┌──────────────┐        │            │
│       │    │  YOLO mode   │        │            │
│       │    │  enabled     │        │            │
│       │    │              │        │            │
│       │    │ ---output--- │        │            │
│       │    │  {JSON RCA}  │        │            │
│       │    │ ---output--- │        │            │
│       │    │  {stats}     │        │            │
│       │    └───────┬──────┘        │            │
│       │            │                │            │
│       │            └────────────────┘            │
│       │                                          │
│       │                                          │
│       └──────┐                                   │
│              │                                   │
│              ▼                                   │
│  ┌────────────────────────────────┐            │
│  │  Context String                │            │
│  │  ┌──────────────────────────┐  │            │
│  │  │ POD_STATUS               │  │            │
│  │  │ PROMETHEUS_METRICS       │  │            │
│  │  │ APPLICATION_LOGS         │  │            │
│  │  │ POD_EVENTS               │  │            │
│  │  │ JFR_ANALYSIS             │  │            │
│  │  │ KRUIZE_RECOMMENDATIONS   │  │            │
│  │  └──────────────────────────┘  │            │
│  └────────────────────────────────┘            │
│                                                 │
└─────────────────────────────────────────────────┘
           │
           │ HTTP/gRPC
           ▼
┌─────────────────────────────┐
│  Kubernetes MCP Server      │
│  (diagnostics-tool ns)      │
│  - Pod Status               │
│  - Events                   │
│  - Logs                     │
│  - Metrics                  │
└─────────────────────────────┘
           │
           │ Kubernetes API
           ▼
┌─────────────────────────────┐
│  Kubernetes Cluster         │
│  - Pods                     │
│  - Events                   │
│  - Logs                     │
└─────────────────────────────┘

           │ HTTP API
           ▼
┌─────────────────────────────┐
│  IBM Bob AI Service         │
│  (cloud.ibm.com)            │
│  - Granite/BOB LLM          │
│  - Tool execution           │
└─────────────────────────────┘
```

---

## Detailed Flow Steps

### Step 1: Alert Reception

**Component:** `AlertWebhook`  
**Location:** `src/main/java/com/causa/api/webhooks/AlertWebhook.java`

```java
POST /api/v1/webhooks/alerts
Content-Type: application/json

{
  "receiver": "causa-webhook",
  "status": "firing",
  "alerts": [{
    "status": "firing",
    "labels": {
      "alertname": "HighMemoryUsage",
      "severity": "critical",
      "pod": "heap-oom-prom-5785ff66b9-nfdqh",
      "namespace": "chaos-test",
      "container": "heap-oom-prom"
    },
    "annotations": {
      "summary": "Pod memory usage at 93%"
    },
    "startsAt": "2026-06-25T12:00:00Z"
  }]
}
```

**Processing:**
1. Validates request structure
2. Checks alert severity (must be "critical")
3. Checks namespace (ignores kube-system, istio-system)
4. Checks cooldown period (15 minutes default)
5. Passes to AlertService

**Log Output:**
```
INFO  [AlertWebhook] Received alert webhook | receiver="causa-webhook", status="firing"
```

---

### Step 2: Alert Transformation

**Component:** `AlertServiceImpl`  
**Location:** `src/main/java/com/causa/core/services/impl/AlertServiceImpl.java`

**Processing:**
1. Extracts alert details from webhook payload
2. Creates Alert entity
3. Generates alert ID: `{container}-{timestamp}`
4. Triggers diagnostic pipeline

**Log Output:**
```
INFO  [AlertServiceImpl] Alert accepted for processing | 
      alertId="heap-oom-prom-1782388320000", 
      alertName="HighMemoryUsage", 
      severity="critical", 
      namespace="chaos-test", 
      podName="heap-oom-prom-5785ff66b9-nfdqh"
```

**Code:**
```java
Alert alert = Alert.builder()
    .alertId(generateAlertId(alertData))
    .alertName(alertData.get("alertname"))
    .severity(alertData.get("severity"))
    .namespace(alertData.get("namespace"))
    .podName(alertData.get("pod"))
    .containerName(alertData.get("container"))
    .summary(annotations.get("summary"))
    .startsAt(parseTimestamp(startsAt))
    .build();

diagnosticService.diagnose(alert);
```

---

### Step 3: MCP Context Collection

**Component:** `McpContextCollector`  
**Location:** `src/main/java/com/causa/mcp/McpContextCollector.java`

**Processing:**
1. Calls Kubernetes MCP server via HTTP
2. Collects pod status, events, logs
3. Formats as structured string with labeled sections

**MCP Server Call:**
```http
POST http://kubernetes-mcp-server.diagnostics-tool.svc.cluster.local:8080/collect
Content-Type: application/json

{
  "namespace": "chaos-test",
  "podName": "heap-oom-prom-5785ff66b9-nfdqh",
  "container": "heap-oom-prom"
}
```

**MCP Response:**
```json
{
  "podStatus": {
    "phase": "Running",
    "restartCount": 3,
    "conditions": [...]
  },
  "events": [...],
  "logs": "...",
  "metrics": {...}
}
```

**Formatted Context String:**
```
## POD STATUS
Pod: heap-oom-prom-5785ff66b9-nfdqh
Namespace: chaos-test
Status: Running
Restart Count: 3

## PROMETHEUS METRICS
CPU Usage: 0.5/1 cores
MEMORY Usage: 478/512 MiB (93.4%)

## APPLICATION LOGS
2026-06-18 09:17:08,004 INFO  [ai.causa.scheduler.DiscoveryScheduler] Inserted 95000 targets. Current registry size=95000
2026-06-18 09:17:08,005 INFO  [ai.causa.scheduler.DiscoveryScheduler] Inserted 96000 targets. Current registry size=96000
...

## POD EVENTS
[Warning] 2026-06-18 08:51:11 UTC: BackOff - Back-off restarting failed container heap-oom-prom
...
```

**Log Output:**
```
DEBUG [McpContextCollector] Collecting MCP context | 
      namespace="chaos-test", 
      pod="heap-oom-prom-5785ff66b9-nfdqh"

DEBUG [McpContextCollector] MCP context collected | 
      contextLength=52341
```

---

### Step 4: Prompt Building

**Component:** `RcaPromptBuilder`  
**Location:** `src/main/java/com/causa/core/services/RcaPromptBuilder.java`

**Processing:**
1. Determines model type from provider/model name
2. Loads appropriate YAML template (`ibm-bob`)
3. Renders template with MCP context

**Model Type Detection:**
```java
// ModelType.from(provider="bob-shell", modelName="bob-shell-1.0.4")
// Returns: ModelType.BOB (because modelName contains "bob")
// Maps to template: "ibm-bob"
```

**Template Loading:**
```yaml
# src/main/resources/prompts/rca-prompt-template.yml
ibm-bob:
  system_prompt: |
    You are an expert Root Cause Analysis (RCA) engine...
    
    CRITICAL: You MUST respond with ONLY valid JSON...
  
  user_prompt: |
    # ROOT CAUSE ANALYSIS PROMPT...
    
    ## OUTPUT REQUIREMENT
    **CRITICAL: Your response must be ONLY a JSON object...**
    
    ## AVAILABLE CONTEXT DATA
    {{context}}
    
    ## OUTPUT FORMAT
    {
      "issue_title": "...",
      "issue_description": "...",
      ...
    }
```

**Final Prompt:**
```
System: You are an expert Root Cause Analysis (RCA) engine specializing in Kubernetes pod memory issues...

CRITICAL: You MUST respond with ONLY valid JSON. Your entire response must be a single JSON object...

User: # ROOT CAUSE ANALYSIS PROMPT FOR KUBERNETES POD MEMORY ISSUES

## OUTPUT REQUIREMENT
**CRITICAL: Your response must be ONLY a JSON object...**

## AVAILABLE CONTEXT DATA

## POD STATUS
Pod: heap-oom-prom-5785ff66b9-nfdqh
Namespace: chaos-test
Status: Running
...

## PROMETHEUS METRICS
CPU Usage: 0.5/1 cores
MEMORY Usage: 478/512 MiB (93.4%)
...

[Full MCP context injected here]

Now analyze the above context and provide your RCA in JSON format.
```

**Log Output:**
```
DEBUG [RcaPromptBuilder] Building RCA prompt | 
      alertId="heap-oom-prom-1782388320000", 
      modelType="BOB", 
      template="ibm-bob"

DEBUG [DiagnosticServiceImpl] RCA prompt built | 
      alertId="heap-oom-prom-1782388320000"
```

---

### Step 5: Bob Shell Execution

**Component:** `BobShellPromptSender`  
**Location:** `src/main/java/com/causa/llm/BobShellPromptSender.java`

#### 5.1: Build Process

```java
ProcessBuilder pb = new ProcessBuilder(
    "bob",                    // Bob Shell CLI
    "--accept-license",       // Accept license automatically
    "--yolo",                 // Auto-approve tool calls
    "-o",                     // Output format flag
    "json"                    // JSON format
);

// Set API key
pb.environment().put("BOBSHELL_API_KEY", apiKey);

// Redirect stderr to stdout
pb.redirectErrorStream(true);

// Start process
Process process = pb.start();
```

#### 5.2: Send Prompt via Stdin

```java
// Write prompt to stdin (handles large prompts >100KB)
try (OutputStreamWriter writer = new OutputStreamWriter(
        process.getOutputStream(), StandardCharsets.UTF_8)) {
    writer.write(prompt);  // Full system + user prompt
    writer.flush();
}
```

**Why Stdin?**
- Handles large prompts (>100KB) without hitting OS ARG_MAX limits
- Avoids shell escaping issues
- More reliable than `-p` flag for long content

#### 5.3: Bob Shell Processing

**Bob Shell Internal Flow:**
```
1. Read prompt from stdin
2. Initialize YOLO mode (auto-approve tools)
3. Send to IBM Bob AI Service API
4. Receive LLM response
5. Format output with markers
6. Return to stdout
```

**Bob Shell Output Format:**
```
YOLO mode is enabled. All tool calls will be automatically approved.
---output---

{
  "issue_title": "Application memory exhaustion due to unbounded target registry growth",
  "issue_description": "The application is running out of memory because it keeps adding more and more monitoring targets to its internal storage without any limits. The memory usage has reached 93% of the available 512 MB, causing the application to crash and restart repeatedly. The system is trying to store over 116,000 targets in memory, which is too much for the current memory allocation.",
  "technical_description": "The pod heap-oom-prom-5785ff66b9-pt87l is experiencing severe memory pressure with usage at 478/512 MiB (93.4% utilization). Analysis of application logs reveals continuous unbounded growth of an in-memory target registry managed by DiscoveryScheduler, which grew from 95,000 to 116,000+ targets during the observation window...",
  "anomaly_type": "POSSIBLE_OOM_KILLED",
  "root_cause": "The application implements an unbounded in-memory registry (DiscoveryScheduler) that continuously accumulates monitoring targets without any size limits, eviction policy, or memory management strategy...",
  "supporting_logs": [
    "2026-06-18 09:17:08,004 INFO  [ai.causa.scheduler.DiscoveryScheduler] (executor-thread-1) Inserted 95000 targets. Current registry size=95000",
    "2026-06-18 09:17:08,005 INFO  [ai.causa.scheduler.DiscoveryScheduler] (executor-thread-1) Inserted 96000 targets. Current registry size=96000"
  ],
  "evidences": [
    "Prometheus Metrics: Memory usage at 478/512 MiB (93.4% utilization), critically high memory pressure",
    "Kubernetes Event (2026-06-18 08:51:11 UTC): BackOff - Back-off restarting failed container heap-oom-prom"
  ],
  "possible_solutions": [
    {
      "solution": "Increase container memory limits to 948 MiB as recommended by Kruize performance analysis",
      "justification": "Kruize analysis, based on actual workload patterns, recommends increasing memory from 512 Mi to 948 Mi...",
      "success_probability": "Medium",
      "implementation_notes": "Update the pod's resource limits..."
    }
  ],
  "llm_confidence_score_for_rca": 0.85,
  "llm_confidence_score_for_solution": 0.9,
  "confidence_summary": "High confidence (0.85) in the root cause analysis due to strong corroborating evidence...",
  "llm_notes": "Started analysis with all available signals: pod status (Running), Kubernetes events (BackOff)..."
}

---output---
{
  "response": "",
  "stats": {
    "models": {
      "premium": {
        "api": {
          "totalRequests": 1,
          "totalErrors": 0,
          "totalLatencyMs": 18234
        },
        "tokens": {
          "prompt": 45123,
          "candidates": 3421,
          "total": 48544,
          "cached": 0
        }
      }
    }
  }
}
```

#### 5.4: Parse Response

```java
// Extract content between ---output--- markers
String[] parts = bobOutput.split("---output---");

// parts[0] = "YOLO mode is enabled..."
// parts[1] = "\n\n{JSON RCA}\n\n"
// parts[2] = "\n{stats JSON}"

String content = parts[1].trim();

// Extract first JSON object (RCA)
int firstBrace = content.indexOf('{');
int braceCount = 0;
for (int i = firstBrace; i < content.length(); i++) {
    if (content.charAt(i) == '{') braceCount++;
    if (content.charAt(i) == '}') braceCount--;
    if (braceCount == 0) {
        String rcaJson = content.substring(firstBrace, i + 1).trim();
        break;
    }
}

// Extract token usage from stats JSON (parts[2])
TokenUsage tokenUsage = extractTokenUsage(parts[2]);
```

**Log Output:**
```
INFO  [BobShellPromptSender] Sending prompt to LLM | 
      provider="bob-shell", 
      model="bob-shell-1.0.4"

WARN  [BobShellPromptSender] DEBUG: Raw Bob Shell output (first 2000 chars) | 
      output="YOLO mode is enabled. All tool calls will be automatically approved.\n---output---\n\n{\"issue_title\":...", 
      totalLength=15234

WARN  [BobShellPromptSender] DEBUG: Split output into parts | 
      partsCount=3

WARN  [BobShellPromptSender] DEBUG: Extracted content between markers (first 1000 chars) | 
      content="{\"issue_title\":\"Application memory exhaustion due to unbounded target registry growth\",\"issue_description\":..."

INFO  [BobShellPromptSender] Prompt sent successfully | 
      model="bob-shell-1.0.4", 
      inputTokens=45123, 
      outputTokens=3421, 
      latencyMs=18234
```

---

### Step 6: RCA Parsing & Storage

**Component:** `DiagnosticServiceImpl.parseRcaResponse()`

**Processing:**
1. Parse JSON string to RootCauseAnalysis object
2. Validate required fields
3. Store in database (TODO)
4. Return to caller

```java
RootCauseAnalysis rca = objectMapper.readValue(
    llmResponse.content(), 
    RootCauseAnalysis.class
);

// Validate
if (rca.getIssueTitle() == null || rca.getAnomalyType() == null) {
    throw new RCAException("Invalid RCA: missing required fields");
}

// TODO: Store in database
// rcaRepository.save(rca);

return rca;
```

**Log Output:**
```
INFO  [DiagnosticServiceImpl] RCA generated successfully | 
      alertId="heap-oom-prom-1782388320000", 
      anomalyType="POSSIBLE_OOM_KILLED", 
      confidenceScore=0.85
```

---

## Configuration

### Build-Time Configuration

```bash
# Build with Bob Shell provider
mvn clean package -Dcausa.llm.provider=bob-shell -DskipTests
```

**Effect:**
- Activates `BobShellPromptSender` (`@IfBuildProperty`)
- Deactivates `LangChainPromptSender` (`@UnlessBuildProperty`)

### Runtime Configuration

**ConfigMap (OpenShift):**
```yaml
LLM_PROVIDER: bob-shell
LLM_MODEL_NAME: bob-shell-1.0.4
CAUSA_MCP_K8S_ENDPOINT: http://kubernetes-mcp-server.diagnostics-tool.svc.cluster.local:8080
CAUSA_MCP_K8S_TIMEOUT: "5000"
```

**Secret:**
```yaml
BOBSHELL_API_KEY: <base64-encoded-key>
```

**Application.yml:**
```yaml
causa:
  llm:
    provider: ${LLM_PROVIDER:bob-shell}
    model-name: ${LLM_MODEL_NAME:bob-shell-1.0.4}
    api-key: ${BOBSHELL_API_KEY:}
    timeout-seconds: 180
    bob:
      shell-path: bob
      timeout-seconds: 180
```

---

## Error Handling

### Scenario 1: No MCP Data

**Trigger:** MCP server is down or returns empty response

**Bob Shell Response:**
```
I cannot perform the Root Cause Analysis without the required input data. 
The task describes a comprehensive RCA framework for Kubernetes pod memory issues, 
but no actual signals or data have been provided.
```

**Handling:**
```java
try {
    RootCauseAnalysis rca = objectMapper.readValue(llmResponse.content(), RootCauseAnalysis.class);
} catch (JsonParseException e) {
    // Bob returned conversational text, not JSON
    log.error("RCA generation failed | alertId={}", alertId)
        .exception(e)
        .log();
    throw new RCAException("Failed to generate RCA: " + e.getMessage());
}
```

**Result:** Error logged, alert processing fails

---

### Scenario 2: Bob Shell Not Found

**Trigger:** Bob Shell not installed in container

**Error:**
```
java.io.IOException: Cannot run program "bob": error=2, No such file or directory
```

**Handling:**
```java
private boolean checkAvailability() {
    try {
        ProcessBuilder pb = new ProcessBuilder("bob", "--version");
        Process process = pb.start();
        boolean completed = process.waitFor(5, TimeUnit.SECONDS);
        return completed && process.exitValue() == 0;
    } catch (IOException | InterruptedException e) {
        log.warn("BOB Shell availability check failed").exception(e).log();
        return false;
    }
}
```

**Result:** Health check fails, app won't start

---

### Scenario 3: API Key Missing

**Trigger:** BOBSHELL_API_KEY not set

**Warning:**
```
WARN  [BobShellPromptSender] LLM_API_KEY environment variable not set
```

**Behavior:** Bob Shell will attempt to run but likely fail authentication with IBM service

---

## Performance Metrics

### Typical Latencies

| Component | Time | Notes |
|-----------|------|-------|
| Alert Reception | < 10ms | HTTP POST processing |
| MCP Collection | 500-2000ms | Depends on K8s API response |
| Prompt Building | < 50ms | Template rendering |
| Bob Shell Execution | 15-30 seconds | LLM inference time |
| JSON Parsing | < 10ms | Jackson parsing |
| **Total** | **~18-35 seconds** | End-to-end |

### Token Usage

**Example from Aakriti's test:**
- Prompt tokens: 45,123
- Completion tokens: 3,421
- Total: 48,544
- Cached: 0 (Bob Shell doesn't support caching)

---

## Comparison: Bob Shell vs LangChain/Claude

| Aspect | Bob Shell | LangChain (Claude) |
|--------|-----------|-------------------|
| **Integration** | CLI process via stdin | REST API |
| **Installation** | Node.js + Bob CLI | No installation |
| **Output Format** | Markers + JSON + Stats | Direct JSON |
| **Token Tracking** | Extracted from stats block | Native in response |
| **Caching** | Not supported | Prompt caching supported |
| **Latency** | 18-30 seconds | 10-20 seconds |
| **Cost** | IBM pricing | Anthropic pricing |
| **JSON Adherence** | ✅ Perfect (with MCP data) | ✅ Perfect |

---

## Success Criteria

✅ **Health Check:** Bob Shell responds "OK"  
✅ **With MCP Data:** Bob returns valid JSON RCA  
✅ **Without MCP Data:** Bob responds "no data" (expected)  
✅ **Token Tracking:** Prompt/completion tokens extracted  
✅ **Error Handling:** Graceful degradation when MCP unavailable  

---

## References

- **Code:** `src/main/java/com/causa/llm/BobShellPromptSender.java`
- **Template:** `src/main/resources/prompts/rca-prompt-template.yml` (ibm-bob section)
- **Config:** `src/main/resources/application.yml`
- **Dockerfile:** `src/main/docker/Dockerfile.jvm`
- **Test Guide:** `docs/BOB-SHELL-TESTING-GUIDE.md`
- **Aakriti's Success:** `docs/BOB-SHELL-SUCCESS.md`

---

**Last Updated:** 2026-06-25  
**Author:** Pinky Gupta  
**Status:** Production-ready, pending MCP server deployment
