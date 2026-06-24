# Testing RCA Generation - Quick Start Guide

## Overview
The RCA generation system is now ready for testing with a realistic test context based on examples from [causa-prompts](https://github.com/shekhar316/causa-prompts/blob/main/master-prompt-with-signals.txt).

## Current Test Context

The `buildTestContext()` method in `DiagnosticServiceImpl` provides a realistic OOM scenario:

### Scenario: High Memory Usage with Registry Growth
- **Pod**: `heap-oom-prom-5785ff66b9-pt87l`
- **Namespace**: `chaos-test`
- **Memory Usage**: 478/512 MiB (93% utilization)
- **Issue**: Unbounded registry growth causing memory pressure
- **Evidence**:
  - Registry growing from 95k to 115k+ targets
  - GC events (DefNew collector, Allocation Failure)
  - BackOff restart warnings
  - JFR showing OutOfMemoryError in exception statistics

### Signals Included:
1. ✅ **POD_STATUS**: Running
2. ✅ **KUBERNETES_EVENTS**: BackOff, container restarts
3. ✅ **PROMETHEUS_METRICS**: CPU 0.071/0.5, Memory 478/512 MiB
4. ✅ **POD_LOGS**: Registry insertion showing unbounded growth
5. ✅ **JFR_CONTAINER_ANALYSIS**: Memory limit 512MB
6. ✅ **JFR_GC_ANALYSIS**: DefNew GC, Allocation Failure, heap usage
7. ✅ **JFR_MEMORY_ANALYSIS**: Heap 17MB used, committed 416MB
8. ✅ **JFR_THREAD_ANALYSIS**: 23 active threads
9. ✅ **JFR_EXCEPTION_ANALYSIS**: OutOfMemoryError count: 1
10. ✅ **KRUIZE_RECOMMENDATIONS**: Increase memory to 948 MiB

## How to Test

### Step 1: Configure LLM Provider

Choose one of the following:

#### Option A: Claude via Anthropic (Recommended for testing)
```yaml
# src/main/resources/application.yml
causa:
  llm:
    provider: anthropic
    model-name: claude-sonnet-4-6
    api-key: ${ANTHROPIC_API_KEY}
    temperature: 0.1
    max-tokens: 4096
```

Set environment variable:
```bash
export ANTHROPIC_API_KEY="sk-ant-..."
```

#### Option B: Bob Shell (IBM)
```yaml
causa:
  llm:
    provider: bob-shell
    model-name: granite-3.1-2b-instruct
    bob:
      shell-path: bob
      api-key: ${BOBSHELL_API_KEY}
```

#### Option C: Ollama (Local)
```yaml
causa:
  llm:
    provider: ollama
    model-name: llama3
    base-url: http://localhost:11434
```

### Step 2: Start the Application

```bash
./mvnw quarkus:dev
```

### Step 3: Trigger an Alert

Create a test alert via the API:

```bash
curl -X POST http://localhost:8080/api/v1/alerts \
  -H "Content-Type: application/json" \
  -d '{
    "alertName": "HighMemoryUsage",
    "severity": "critical",
    "podName": "heap-oom-prom-5785ff66b9-pt87l",
    "namespace": "chaos-test",
    "containerName": "heap-oom-prom",
    "labels": {
      "app": "heap-oom-prom",
      "env": "test"
    }
  }'
```

### Step 4: Check Logs

Watch for RCA generation logs:

```bash
# Look for these log messages:
[INFO] Building LLM context
[INFO] RCA prompt built - systemPromptLength: X, userPromptLength: Y
[INFO] LLM response received - modelUsed: claude-sonnet-4-6, inputTokens: X, outputTokens: Y
[INFO] RCA generated successfully - anomalyType: POSSIBLE_OOM_KILLED, rcaConfidence: 0.85
```

### Step 5: Verify RCA Output

The RCA should identify:
- **Anomaly Type**: `POSSIBLE_OOM_KILLED` or `OOM_KILLED`
- **Root Cause**: Unbounded registry growth consuming heap memory
- **Evidence**:
  - Memory usage at 93% (478/512 MiB)
  - Registry growing continuously (95k → 115k targets)
  - OutOfMemoryError in JFR exception stats
  - BackOff restart events
- **Solutions**:
  - Increase memory limits to 948 MiB (per Kruize recommendation)
  - Implement registry size limits
  - Add memory leak detection
- **Confidence**: Should be high (0.7-0.9) due to clear evidence

## Modifying Test Context

To test different scenarios, edit `buildTestContext()` in `DiagnosticServiceImpl.java`:

### Example 1: OOM Killed Scenario
```java
return """
    ## POD STATUS
    Pod: crashed-pod
    Status: CrashLoopBackOff
    
    ## KUBERNETES EVENTS
    [Warning] 2026-06-23T10:00:00Z: OOMKilled - Container exceeded memory limit (exit code 137)
    
    ## POD LOGS
    java.lang.OutOfMemoryError: Java heap space
        at com.example.MemoryLeak.allocate()
    
    ## PROMETHEUS METRICS
    MEMORY Usage: 512/512 MiB
    """;
```

### Example 2: GC Pause Scenario
```java
return """
    ## POD STATUS
    Pod: slow-app
    Status: Running
    
    ## JFR GC ANALYSIS
    {
      "events": {
        "jdk.GarbageCollection": {
          "rows": [
            {"name": "G1Old", "duration": "150000000", "cause": "G1 Humongous Allocation"}
          ]
        }
      }
    }
    
    ## PROMETHEUS METRICS
    MEMORY Usage: 450/512 MiB
    """;
```

### Example 3: Healthy Pod
```java
return """
    ## POD STATUS
    Pod: healthy-app
    Status: Running
    
    ## PROMETHEUS METRICS
    CPU Usage: 0.050/1.000 cores
    MEMORY Usage: 200/512 MiB
    
    ## POD LOGS
    2026-06-23 10:00:00 INFO Application started successfully
    """;
```

## Expected Output Format

The LLM should return JSON matching the `RootCauseAnalysis` schema:

```json
{
  "issue_title": "Memory Exhaustion Due to Unbounded Registry Growth",
  "issue_description": "The application ran out of memory because it kept adding items to a registry without any size limit...",
  "technical_description": "The pod heap-oom-prom-5785ff66b9-pt87l is experiencing memory pressure at 93% utilization (478/512 MiB)...",
  "anomaly_type": "POSSIBLE_OOM_KILLED",
  "root_cause": "The DiscoveryScheduler continuously inserts targets into an in-memory registry...",
  "supporting_logs": [
    "2026-06-18 09:17:08,004 INFO [ai.causa.scheduler.DiscoveryScheduler] Inserted 95000 targets. Current registry size=95000",
    "2026-06-18 09:17:28,008 INFO [ai.causa.scheduler.DiscoveryScheduler] Inserted 115000 targets. Current registry size=115000"
  ],
  "evidences": [
    "Memory usage at 93%: 478/512 MiB (Prometheus)",
    "Registry size growing from 95,000 to 115,000+ targets in 20 seconds",
    "OutOfMemoryError count: 1 (JFR Exception Analysis)",
    "Pod BackOff restart event at 08:51:11 UTC"
  ],
  "possible_solutions": [
    {
      "solution": "Increase memory limit to 948 MiB",
      "justification": "Kruize recommends +436 MiB increase based on usage patterns...",
      "success_probability": "Medium",
      "implementation_notes": "Update deployment YAML memory limits..."
    },
    {
      "solution": "Implement maximum size limit for registry",
      "justification": "Logs show unbounded growth...",
      "success_probability": "High",
      "implementation_notes": "Add LRU cache with max size..."
    }
  ],
  "llm_confidence_score_for_rca": 0.85,
  "llm_confidence_score_for_solution": 0.75,
  "confidence_summary": "High confidence in root cause due to clear evidence...",
  "llm_notes": "Analysis started with pod status showing Running state..."
}
```

## Switching to Real MCP Context

Once MCP integration is complete, uncomment this line in `buildContextForLLM()`:

```java
// CURRENT (test mode):
String contextString = buildTestContext(alert);

// CHANGE TO (production mode):
String contextString = mcpContextCollector.collectContextAsString(alert);
```

## Troubleshooting

### Issue: LLM returns plain text instead of JSON
**Solution**: Check your model and prompt template. Bob/Granite models may need more explicit JSON instructions.

### Issue: JSON parsing fails
**Solution**: 
1. Check logs for the raw LLM response
2. The `parseRcaResponse()` method strips markdown code blocks
3. Verify the JSON schema matches `RootCauseAnalysis` exactly

### Issue: Low confidence scores
**Solution**: This is expected if signals are missing. Add more context signals (JFR, Prometheus, etc.) for higher confidence.

### Issue: Wrong anomaly type (HEALTHY when should be OOM)
**Solution**: 
1. Ensure memory usage is shown as high percentage (>75%)
2. Include OOM errors in logs
3. Add BackOff or OOMKilled events

## Context Reference

Full context format and more examples:
- **Repository**: https://github.com/shekhar316/causa-prompts
- **Example File**: https://github.com/shekhar316/causa-prompts/blob/main/master-prompt-with-signals.txt

## Next Steps After Testing

1. **Validate Output**: Ensure RCA correctly identifies issues
2. **Test Edge Cases**: Healthy pods, GC pauses, actual OOM kills
3. **Model Comparison**: Compare Claude vs Bob vs Ollama output quality
4. **Performance**: Measure latency and token usage
5. **Integration**: Connect to real MCP data sources
6. **Persistence**: Store RCA results in database
7. **API**: Add endpoint to retrieve RCA for an alert
