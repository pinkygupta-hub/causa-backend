# RCA Testing Execution Guide

## Quick Start

### 1. Deploy to OpenShift
```bash
cd /Users/pinkygupta/Documents/workspace/causa-backend

# Make sure you're on the testing branch
git checkout rca-llm-integration

# Deploy
./deploy-for-testing.sh
```

### 2. Update Test Contexts (One-time setup)
Since we cannot access causa-prompts repo directly, you need to manually download test contexts:

```bash
# Create test contexts directory if it doesn't exist
mkdir -p src/main/resources/test-contexts

# For each test T1-T9, download context.txt from:
# https://github.com/shekhar316/causa-prompts/tree/main/tests/t{N}-{description}/context.txt
# and save as src/main/resources/test-contexts/t{N}-context.txt
```

**Example**:
```bash
# Download T1 context
curl -o src/main/resources/test-contexts/t1-context.txt \
  https://raw.githubusercontent.com/shekhar316/causa-prompts/main/tests/t1-normal-all-data-raw-jfr/context.txt

# Repeat for T2-T9
```

### 3. Update Code to Load Test Contexts

Edit `src/main/java/com/causa/core/services/impl/DiagnosticServiceImpl.java`:

```java
private String buildTestContext(Alert alert) {
    // Determine which test context to load based on alert name or pod name
    String testFile = "/test-contexts/t1-context.txt"; // Default
    
    // If alert name contains T2, T3, etc., load corresponding context
    if (alert.getAlertName().contains("T2")) {
        testFile = "/test-contexts/t2-context.txt";
    } else if (alert.getAlertName().contains("T3")) {
        testFile = "/test-contexts/t3-context.txt";
    }
    // ... continue for T4-T9
    
    try (InputStream is = getClass().getResourceAsStream(testFile)) {
        if (is == null) {
            throw new RuntimeException("Test context file not found: " + testFile);
        }
        return new String(is.readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException e) {
        throw new RuntimeException("Failed to load test context from classpath", e);
    }
}
```

### 4. Rebuild and Redeploy
```bash
./deploy-for-testing.sh
```

### 5. Run Test Scenarios
```bash
./test-rca-scenarios.sh
```

---

## Manual Testing (Alternative)

If the automated script doesn't work, test manually:

### Test T1
```bash
ROUTE=$(oc get route -n pinky causa-backend -o jsonpath='{.spec.host}')

curl -k -X POST "https://$ROUTE/webhook/alertmanager" \
  -H "Content-Type: application/json" \
  -d '{
    "alerts": [{
      "status": "firing",
      "labels": {
        "alertname": "HighMemoryUsage_T1",
        "severity": "critical",
        "pod": "test-pod-t1",
        "namespace": "test-namespace",
        "container": "test-container"
      },
      "annotations": {
        "summary": "Test scenario T1"
      }
    }]
  }'

# Wait 60 seconds
sleep 60

# View logs
oc logs -n pinky -l app.kubernetes.io/name=causa-backend --tail=200 | grep -A 50 "PARSED RCA OUTPUT"
```

### Repeat for T2-T9
Change `alertname` to `HighMemoryUsage_T2`, `HighMemoryUsage_T3`, etc.

---

## Analyzing Results

### 1. Extract RCA Outputs
Results are in `docs/tmp-pinky/test-results/`:
- `t{N}-full-log.txt` - Complete logs
- `t{N}-rca-summary.txt` - Extracted RCA output
- `t{N}-context.txt` - Context sent to LLM

### 2. Fill Out Correctness Analysis
Use the template: `RCA-CORRECTNESS-ANALYSIS-TEMPLATE.md`

For each test case, evaluate:
1. **Anomaly Detection**: Is the anomaly_type correct?
2. **Root Cause**: Does it match the expected root cause?
3. **Evidence**: Are all evidences from the provided context?
4. **Solutions**: Are they actionable and specific?
5. **Confidence**: Does it match signal availability?

### 3. Check for Common Issues

**Hallucinations**:
```bash
# Search for evidence that doesn't exist in context
grep "Evidence" t1-rca-summary.txt
# Then manually verify each evidence exists in t1-context.txt
```

**Confidence Calibration**:
```bash
# Extract all confidence scores
for i in {1..9}; do
    echo -n "T$i: "
    grep "llm_confidence_score_for_rca" test-results/t${i}-rca-summary.txt | head -1
done

# Expected pattern:
# T1-T5: 0.8-0.9 (full context)
# T6-T8: 0.6-0.7 (missing 1 signal)
# T9: 0.4-0.6 (missing 2 signals)
```

**Anomaly Detection Accuracy**:
```bash
# Extract all anomaly types
for i in {1..9}; do
    echo -n "T$i: "
    grep "Anomaly Type:" test-results/t${i}-rca-summary.txt | head -1
done

# Expected: All should be POSSIBLE_OOM_KILLED or OOM_KILLED
```

---

## Expected Results

### T1: Normal - All Data
- **Anomaly**: POSSIBLE_OOM_KILLED
- **Confidence**: 0.85-0.90
- **Root Cause**: Unbounded registry growth (95k→115k targets)
- **Evidence Count**: 8-10
- **Solutions**: 2-3 specific solutions (increase memory, fix memory leak, optimize registry)

### T2: Missing Pod Events
- **Anomaly**: POSSIBLE_OOM_KILLED
- **Confidence**: 0.70-0.80
- **Root Cause**: Same as T1, but without event-based evidence
- **Evidence Count**: 6-8
- **Solutions**: Similar to T1
- **Note**: Should mention missing events in llm_notes

### T3: Normal - JFR from MCP
- **Anomaly**: POSSIBLE_OOM_KILLED
- **Confidence**: 0.85-0.90
- **Root Cause**: Same as T1
- **Evidence Count**: 8-10
- **Note**: JFR format may be different (structured JSON vs raw)

### T4-T5: Variations
- Similar to T1-T3 with different prompt/context formats
- Confidence should remain high (0.80-0.90)

### T6: Missing JFR
- **Anomaly**: POSSIBLE_OOM_KILLED
- **Confidence**: 0.65-0.75
- **Evidence Count**: 5-7
- **Note**: Cannot cite GC or heap pool data
- **Note**: Should mention missing JFR in confidence_summary

### T7: Missing Kruize
- **Anomaly**: POSSIBLE_OOM_KILLED
- **Confidence**: 0.75-0.85
- **Evidence Count**: 7-9
- **Note**: Cannot cite resource recommendations
- **Solutions**: May be less specific about exact memory values

### T8: Missing Logs
- **Anomaly**: POSSIBLE_OOM_KILLED
- **Confidence**: 0.60-0.70
- **Supporting Logs**: ["No direct supporting logs present"]
- **Evidence Count**: 5-7
- **Note**: Relies on metrics, events, and JFR only

### T9: Missing Logs & Events
- **Anomaly**: POSSIBLE_OOM_KILLED or POSSIBLE_GC_PAUSE
- **Confidence**: 0.45-0.60
- **Supporting Logs**: ["No direct supporting logs present"]
- **Evidence Count**: 4-6
- **Note**: Should explicitly state uncertainty
- **Note**: Should mention both missing signals

---

## Troubleshooting

### Deployment Issues
```bash
# Check pod status
oc get pods -n pinky -l app.kubernetes.io/name=causa-backend

# View full logs
oc logs -n pinky -l app.kubernetes.io/name=causa-backend -f

# Restart deployment
oc rollout restart deployment/causa-backend -n pinky
```

### RCA Not Generating
```bash
# Check if alert was received
oc logs -n pinky -l app.kubernetes.io/name=causa-backend | grep "Alert received"

# Check for errors
oc logs -n pinky -l app.kubernetes.io/name=causa-backend | grep -i error

# Check LLM connectivity
oc logs -n pinky -l app.kubernetes.io/name=causa-backend | grep "LLM"
```

### Context Not Loading
```bash
# Verify test context files are in the image
oc exec -n pinky deployment/causa-backend -- ls -la /deployments/app/classes/test-contexts/

# Check which context is being loaded
oc logs -n pinky -l app.kubernetes.io/name=causa-backend | grep "Test context file"
```

---

## Documentation Checklist

After completing all tests:

- [ ] All T1-T9 tests executed successfully
- [ ] RCA outputs captured for all scenarios
- [ ] Correctness analysis completed for each test
- [ ] Hallucinations identified and documented
- [ ] Confidence calibration analyzed
- [ ] Solution quality evaluated
- [ ] Summary statistics calculated
- [ ] Key findings documented
- [ ] Recommendations provided
- [ ] Raw outputs saved for reference

---

## Timeline

Estimated time: **3-4 hours**

- Setup and deployment: 30 min
- Test execution: 90 min (9 tests × 10 min each)
- Analysis and documentation: 60-90 min

---

## Success Criteria

✅ **Pass**: 
- 100% anomaly detection accuracy (all POSSIBLE_OOM_KILLED or OOM_KILLED)
- Confidence calibration: scores decrease as signals are removed
- No hallucinations in evidence
- Solutions are actionable and specific
- llm_notes mentions missing signals

⚠️ **Partial Pass**:
- 80%+ anomaly detection accuracy
- Minor confidence calibration issues
- Occasional generic solutions
- Minor hallucinations (non-critical evidence)

❌ **Fail**:
- <80% anomaly detection accuracy
- Confidence does not correlate with signal availability
- Frequent hallucinations
- Solutions are mostly generic or incorrect
- Does not mention missing signals
