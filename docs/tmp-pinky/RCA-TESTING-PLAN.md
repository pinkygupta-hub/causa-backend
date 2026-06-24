# RCA Testing Plan - T1 to T9 Scenarios

**Date**: 2026-06-24  
**Branch**: rca-llm-integration  
**Test Source**: https://github.com/shekhar316/causa-prompts/tree/main/tests

## Test Scenarios Overview

### T1: Normal - All Data with Raw JFR
- **Description**: Complete context with all signals (POD_STATUS, EVENTS, METRICS, LOGS, JFR, KRUIZE)
- **Expected**: POSSIBLE_OOM_KILLED or OOM_KILLED
- **Confidence**: High (0.8-0.9)

### T2: Missing Pod Events
- **Description**: All signals except POD_EVENTS
- **Expected**: POSSIBLE_OOM_KILLED (lower confidence without events)
- **Confidence**: Medium (0.6-0.7)

### T3: Normal - All Data with JFR from MCP
- **Description**: Complete context with JFR data from MCP (structured format)
- **Expected**: POSSIBLE_OOM_KILLED or OOM_KILLED
- **Confidence**: High (0.8-0.9)

### T4: Normal - Prompt Reductions
- **Description**: Optimized/reduced prompt with all data
- **Expected**: POSSIBLE_OOM_KILLED
- **Confidence**: High (0.7-0.9)

### T5: Normal - Context Reduction
- **Description**: Reduced context size while maintaining key signals
- **Expected**: POSSIBLE_OOM_KILLED
- **Confidence**: Medium-High (0.7-0.8)

### T6: Normal - Missing JFR
- **Description**: All signals except JFR analysis
- **Expected**: POSSIBLE_OOM_KILLED
- **Confidence**: Medium (0.6-0.7)

### T7: Normal - Missing Kruize Recommendations
- **Description**: All signals except KRUIZE_RECOMMENDATIONS
- **Expected**: POSSIBLE_OOM_KILLED
- **Confidence**: Medium-High (0.7-0.8)

### T8: Normal - Missing Logs
- **Description**: All signals except APPLICATION_LOGS
- **Expected**: POSSIBLE_OOM_KILLED
- **Confidence**: Medium (0.5-0.7)

### T9: Normal - Missing Logs and Events
- **Description**: Only POD_STATUS, METRICS, JFR, KRUIZE (no logs or events)
- **Expected**: POSSIBLE_OOM_KILLED or POSSIBLE_GC_PAUSE
- **Confidence**: Low-Medium (0.4-0.6)

## Deployment Steps

### 1. Build Image
```bash
# From causa-backend directory
mvn clean package -DskipTests
docker build -t quay.io/rh-ee-shesaxen/causa-backend:rca-testing .
docker push quay.io/rh-ee-shesaxen/causa-backend:rca-testing
```

### 2. Update Kustomization
```bash
# Update image tag in /tmp/causa-deploy-pinky/kustomization.yaml
newTag: rca-testing
```

### 3. Deploy to OpenShift
```bash
oc apply -k /tmp/causa-deploy-pinky/
oc rollout status deployment/causa-backend -n pinky
```

### 4. Verify Deployment
```bash
# Check pods
oc get pods -n pinky -l app.kubernetes.io/name=causa-backend

# Check logs
oc logs -n pinky -l app.kubernetes.io/name=causa-backend --tail=50

# Test health
ROUTE=$(oc get route -n pinky causa-backend -o jsonpath='{.spec.host}')
curl -k "https://$ROUTE/q/health/live"
```

## Testing Steps

### For Each Test Case (T1-T9):

1. **Prepare Test Context**
   - Download context file from causa-prompts repo
   - Save to `src/main/resources/test-contexts/t{N}-context.txt`
   - Update `buildTestContext()` to load the specific file

2. **Trigger RCA**
   ```bash
   curl -k -X POST "https://$ROUTE/webhook/alertmanager" \
     -H "Content-Type: application/json" \
     -d '{
       "alerts": [{
         "status": "firing",
         "labels": {
           "alertname": "HighMemoryUsage",
           "severity": "critical",
           "pod": "test-pod-t{N}",
           "namespace": "test-namespace",
           "container": "test-container"
         },
         "annotations": {
           "summary": "Test scenario T{N}"
         }
       }]
     }'
   ```

3. **Capture Output**
   ```bash
   # View RCA output from logs
   oc logs -n pinky -l app.kubernetes.io/name=causa-backend --tail=200 | \
     grep -A 50 "PARSED RCA OUTPUT"
   ```

4. **Extract Key Metrics**
   - Anomaly Type (OOM_KILLED, POSSIBLE_OOM_KILLED, etc.)
   - RCA Confidence Score
   - Solution Confidence Score
   - Number of Solutions
   - Root Cause Summary
   - Evidence Count

## Documentation Template

For each test case, document:

### T{N}: {Scenario Name}

**Context Provided**:
- POD_STATUS: ✅/❌
- POD_EVENTS: ✅/❌
- PROMETHEUS_METRICS: ✅/❌
- APPLICATION_LOGS: ✅/❌
- JFR_ANALYSIS: ✅/❌
- KRUIZE_RECOMMENDATIONS: ✅/❌

**RCA Output**:
```json
{
  "issue_title": "...",
  "anomaly_type": "...",
  "llm_confidence_score_for_rca": X.XX,
  "llm_confidence_score_for_solution": X.XX,
  "root_cause": "...",
  "possible_solutions": [...]
}
```

**Correctness Analysis**:
- ✅ **Anomaly Detection**: Correct/Incorrect - Explanation
- ✅ **Root Cause**: Accurate/Partially Accurate/Inaccurate - Explanation
- ✅ **Evidence Quality**: Strong/Medium/Weak - Explanation
- ✅ **Solutions**: Actionable/Generic/Poor - Explanation
- ✅ **Confidence Scores**: Appropriate/Too High/Too Low - Explanation

**Overall Grade**: A/B/C/D/F

**Notes**: Any observations about LLM behavior, hallucinations, or insights

---

## Success Criteria

- **Anomaly Detection**: 100% correct classification for all T1-T9
- **RCA Confidence**: Scores correlate with signal availability (high for T1, lower for T9)
- **No Hallucinations**: All evidence must be from provided context
- **Solution Quality**: At least 2 actionable solutions per scenario
- **Graceful Degradation**: Lower confidence when signals are missing (T6-T9)

## Next Steps

1. Deploy updated code
2. Run all 9 test scenarios
3. Document results in RCA-TEST-RESULTS.md
4. Analyze patterns and identify improvements
5. Update prompts if needed based on findings
