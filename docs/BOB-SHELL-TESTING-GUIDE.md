# Bob Shell Testing Guide

**Date:** 2026-06-25  
**Branch:** `rca-llm-integration-testing`  
**Purpose:** Step-by-step guide to test Bob Shell integration for RCA generation

---

## Table of Contents

1. [Prerequisites](#prerequisites)
2. [Testing Scenarios](#testing-scenarios)
3. [Test Execution Steps](#test-execution-steps)
4. [Expected Results](#expected-results)
5. [Troubleshooting](#troubleshooting)
6. [Verification Commands](#verification-commands)

---

## Prerequisites

### Required Components

#### 1. Bob Shell Integration (✅ Complete)
- Branch: `rca-llm-integration-testing`
- Image: `quay.io/pingupta/irb:bob-shell-test`
- Deployed: OpenShift `pinky` namespace
- Status: Running and healthy

#### 2. Bob API Key (✅ Configured)
```bash
# Verify Bob API key is set
oc get secret causa-llm-secrets -n pinky -o yaml | grep BOBSHELL_API_KEY

# Should show: BOBSHELL_API_KEY: <base64-encoded-key>
```

#### 3. MCP Server (❌ Required for Full Testing)
```bash
# Check if MCP server is running
oc get pods -n diagnostics-tool | grep mcp

# If not running, deploy:
# oc apply -f deployment/kubernetes/mcp-server.yaml
```

#### 4. Test Pod (✅ Available)
```bash
# Verify test pod exists
oc get pod -n chaos-test heap-oom-prom-5785ff66b9-nfdqh

# Output:
# NAME                              READY   STATUS    RESTARTS      AGE
# heap-oom-prom-5785ff66b9-nfdqh   1/1     Running   3 (27h ago)   29h
```

---

## Testing Scenarios

### Scenario 1: Health Check Test (Baseline)
**Purpose:** Verify Bob Shell installation and connectivity  
**MCP Required:** No  
**Expected:** Bob Shell returns "OK"

### Scenario 2: No MCP Data Test
**Purpose:** Verify Bob Shell behavior without MCP context  
**MCP Required:** No  
**Expected:** Bob Shell responds "cannot proceed without data"

### Scenario 3: Full RCA Generation (End-to-End)
**Purpose:** Verify Bob Shell generates JSON RCA with MCP context  
**MCP Required:** Yes ✅ **This is the main test**  
**Expected:** Bob Shell returns perfect JSON RCA (like Aakriti's test)

### Scenario 4: Local Testing with Mock Context
**Purpose:** Test with pre-saved context files (T1, T14)  
**MCP Required:** No  
**Expected:** Bob Shell generates RCA from mock data

---

## Test Execution Steps

### Test 1: Verify Bob Shell Health Check ✅ PASSED

**Objective:** Confirm Bob Shell is installed and responding

```bash
# 1. Check deployment is running
oc get pods -n pinky | grep causa-backend

# 2. Check pod logs for Bob Shell initialization
POD=$(oc get pods -n pinky --no-headers | grep causa-backend | grep Running | head -1 | awk '{print $1}')
oc logs -n pinky $POD | grep "BOB Shell"

# Expected output:
# BOB Shell is available and ready | shell_path="bob"
# Sending prompt to LLM | provider="bob-shell", model="bob-shell-1.0.4"
# DEBUG: Extracted content between markers: "OK"
# LLM connectivity verified | provider="bob-shell", latencyMs=9736
# LLM ready | provider="bob-shell"

# 3. Check health endpoint
ROUTE="causa-backend-pinky.apps.cluster-ns4r6.ns4r6.sandbox151.opentlc.com"
curl -k "https://$ROUTE/q/health/ready" | jq

# Expected:
# {
#   "status": "UP",
#   "checks": [
#     {
#       "name": "llm",
#       "status": "UP"
#     }
#   ]
# }
```

**Result:** ✅ PASSED - Bob Shell is healthy and ready

---

### Test 2: Trigger Alert Without MCP Server

**Objective:** Verify Bob Shell behavior when no context data is available

```bash
ROUTE="causa-backend-pinky.apps.cluster-ns4r6.ns4r6.sandbox151.opentlc.com"

# Trigger alert for real pod (but MCP server not running)
curl -k -X POST "https://$ROUTE/api/v1/webhooks/alerts" \
  -H "Content-Type: application/json" \
  -d '{
    "receiver": "causa-webhook",
    "status": "firing",
    "alerts": [{
      "status": "firing",
      "labels": {
        "alertname": "BobTestNoMCP",
        "severity": "critical",
        "pod": "heap-oom-prom-5785ff66b9-nfdqh",
        "namespace": "chaos-test",
        "container": "heap-oom-prom"
      },
      "annotations": {
        "summary": "Testing Bob Shell without MCP data"
      },
      "startsAt": "2026-06-25T12:00:00Z"
    }]
  }'

# Wait for processing
sleep 30

# Check logs
POD=$(oc get pods -n pinky --no-headers | grep causa-backend | grep Running | head -1 | awk '{print $1}')
oc logs -n pinky $POD --tail=100 | grep -A 5 "BobTestNoMCP"
```

**Expected Result:**
```
DEBUG: Extracted content: "I cannot perform the Root Cause Analysis without the required 
input data. The task describes a comprehensive RCA framework for Kubernetes pod memory 
issues, but no actual signals or data have been provided."

ERROR: JsonParseException: Unrecognized token 'I'
```

**Interpretation:** ✅ Bob Shell is working correctly - it's responding appropriately to lack of data

---

### Test 3: Full RCA with MCP Server ⭐ MAIN TEST

**Objective:** Generate complete RCA with Bob Shell using real MCP context

#### Step 1: Deploy MCP Server (If Not Running)

```bash
# Check if MCP server exists
oc get pods -n diagnostics-tool | grep mcp

# If not found, deploy MCP server:
# 1. Get MCP deployment manifest
# 2. Deploy to diagnostics-tool namespace
# 3. Verify it's running

oc get svc -n diagnostics-tool | grep mcp
# Expected: kubernetes-mcp-server service on port 8080

# Test MCP server health
oc exec -n pinky $(oc get pods -n pinky -o name | grep causa-backend | head -1) -- \
  curl -s http://kubernetes-mcp-server.diagnostics-tool.svc.cluster.local:8080/healthz
```

#### Step 2: Trigger Alert for Real Pod

```bash
ROUTE="causa-backend-pinky.apps.cluster-ns4r6.ns4r6.sandbox151.opentlc.com"

# Trigger alert with MCP server running
curl -k -X POST "https://$ROUTE/api/v1/webhooks/alerts" \
  -H "Content-Type: application/json" \
  -d '{
    "receiver": "causa-webhook",
    "status": "firing",
    "alerts": [{
      "status": "firing",
      "labels": {
        "alertname": "BobShellFullRCA",
        "severity": "critical",
        "pod": "heap-oom-prom-5785ff66b9-nfdqh",
        "namespace": "chaos-test",
        "container": "heap-oom-prom"
      },
      "annotations": {
        "summary": "Full RCA test with Bob Shell + MCP"
      },
      "startsAt": "2026-06-25T12:30:00Z"
    }]
  }' | jq

# Response should be 202 Accepted
```

#### Step 3: Monitor Processing

```bash
# Follow logs in real-time
POD=$(oc get pods -n pinky --no-headers | grep causa-backend | grep Running | head -1 | awk '{print $1}')
oc logs -n pinky $POD -f | grep -E "BobShellFullRCA|MCP|RCA|Bob"

# In another terminal, check for completion
watch -n 5 "oc logs -n pinky $POD --tail=50 | grep 'RCA generated successfully'"
```

#### Step 4: Retrieve RCA Result

```bash
# Get alert ID from logs
ALERT_ID=$(oc logs -n pinky $POD --tail=200 | grep "BobShellFullRCA" | grep "alertId=" | sed -n 's/.*alertId="\([^"]*\)".*/\1/p' | head -1)

echo "Alert ID: $ALERT_ID"

# Get RCA from database (if stored)
# Or check logs for the generated RCA JSON
oc logs -n pinky $POD --tail=500 | grep -A 100 "issue_title"
```

**Expected Result:** Perfect JSON RCA similar to Aakriti's test:

```json
{
  "issue_title": "Application memory exhaustion due to unbounded target registry growth",
  "issue_description": "The application is running out of memory because it keeps adding more and more monitoring targets...",
  "technical_description": "The pod heap-oom-prom-5785ff66b9-nfdqh is experiencing severe memory pressure with usage at 478/512 MiB (93.4% utilization)...",
  "anomaly_type": "POSSIBLE_OOM_KILLED",
  "root_cause": "The application implements an unbounded in-memory registry (DiscoveryScheduler)...",
  "supporting_logs": [
    "2026-06-18 09:17:08,004 INFO  [ai.causa.scheduler.DiscoveryScheduler] (executor-thread-1) Inserted 95000 targets. Current registry size=95000",
    ...
  ],
  "evidences": [
    "Prometheus Metrics: Memory usage at 478/512 MiB (93.4% utilization)",
    "Kubernetes Event (2026-06-18 08:51:11 UTC): BackOff - Back-off restarting failed container",
    ...
  ],
  "possible_solutions": [
    {
      "solution": "Increase container memory limits to 948 MiB as recommended by Kruize",
      "justification": "Kruize analysis recommends increasing memory from 512 Mi to 948 Mi...",
      "success_probability": "Medium",
      "implementation_notes": "Update pod's resource limits..."
    },
    ...
  ],
  "llm_confidence_score_for_rca": 0.85,
  "llm_confidence_score_for_solution": 0.9,
  "confidence_summary": "High confidence (0.85) in the root cause analysis...",
  "llm_notes": "Started analysis with all available signals: pod status (Running), Kubernetes events (BackOff)..."
}
```

---

### Test 4: Local Testing with Mock Context

**Objective:** Test Bob Shell with pre-saved context files (T1, T14)

#### Step 1: Start Application Locally

```bash
cd /Users/pinkygupta/Documents/workspace/causa-backend

# Switch to testing branch if not already
git checkout rca-llm-integration-testing

# Build with Bob Shell
mvn clean package -Dcausa.llm.provider=bob-shell -DskipTests

# Set Bob API key
export BOBSHELL_API_KEY="<your-bob-api-key>"

# Run locally
./mvnw quarkus:dev
```

#### Step 2: Run Test Script

```bash
# In another terminal
cd /Users/pinkygupta/Documents/workspace/causa-backend

# Test T1 scenario (JFR + Kruize + High Memory)
./test-rca.sh T1

# Test T14 scenario (No JFR + Moderate Memory)
./test-rca.sh T14
```

#### Step 3: Check Results

```bash
# View results
cat docs/tmp-pinky/test-results/bob-shell-T1-rca.txt
cat docs/tmp-pinky/test-results/bob-shell-T1-log.txt
```

**Expected:** JSON RCA generated based on mock context

---

## Expected Results

### Success Indicators ✅

1. **Health Check**
   - ✅ "BOB Shell is available and ready"
   - ✅ "LLM connectivity verified"
   - ✅ Health endpoint returns `"status": "UP"`

2. **With MCP Data**
   - ✅ "MCP context collected | contextLength=50000+"
   - ✅ "Bob Shell executed successfully"
   - ✅ "DEBUG: Extracted content: {\"issue_title\":..."
   - ✅ "RCA generated successfully"
   - ✅ JSON RCA stored in database

3. **Without MCP Data**
   - ✅ Bob responds: "cannot proceed without data"
   - ✅ JsonParseException logged (expected - no data to analyze)

### Failure Indicators ❌

1. **Bob Shell Not Installed**
   ```
   ERROR: BOB Shell is not available
   ERROR: Command 'bob' not found
   ```

2. **API Key Missing**
   ```
   WARN: LLM_API_KEY environment variable not set
   ERROR: Authentication failed
   ```

3. **MCP Server Down** (when expected to be up)
   ```
   ERROR: Failed to connect to MCP server
   WARN: MCP context empty
   ```

4. **Unexpected JSON Parse Error** (with MCP data)
   ```
   ERROR: JsonParseException: Unrecognized token 'I'
   # When MCP is running, this should NOT happen
   ```

---

## Troubleshooting

### Issue 1: Bob Shell Not Found

**Symptoms:**
```
ERROR: BOB Shell is not available
ERROR: bob: command not found
```

**Solution:**
```bash
# Check Dockerfile includes Bob Shell installation
grep -A 5 "Install BOB Shell" src/main/docker/Dockerfile.jvm

# Rebuild image
mvn clean package -Dcausa.llm.provider=bob-shell -DskipTests
docker build -f src/main/docker/Dockerfile.jvm -t quay.io/pingupta/irb:bob-shell-fix .
podman push quay.io/pingupta/irb:bob-shell-fix

# Redeploy
oc set image deployment/causa-backend -n pinky causa-backend=quay.io/pingupta/irb:bob-shell-fix
```

---

### Issue 2: Wrong PromptSender Activated

**Symptoms:**
```
ERROR: LangChainPromptSender used instead of BobShellPromptSender
ERROR: LLM chat model not available | provider="bob-shell"
```

**Cause:** Build property not set during Maven build

**Solution:**
```bash
# MUST build with -Dcausa.llm.provider=bob-shell
mvn clean package -Dcausa.llm.provider=bob-shell -DskipTests

# Verify BobShellPromptSender is active
grep "@IfBuildProperty" src/main/java/com/causa/llm/BobShellPromptSender.java
# Should show: @IfBuildProperty(name = "causa.llm.provider", stringValue = "bob-shell")
```

---

### Issue 3: MCP Context Empty

**Symptoms:**
```
WARN: MCP context empty | contextLength=0
INFO: Sending prompt with no context
Bob Response: "I cannot proceed without data"
```

**Diagnosis:**
```bash
# 1. Check MCP server is running
oc get pods -n diagnostics-tool | grep mcp

# 2. Check MCP server health
oc exec -n pinky $(oc get pods -n pinky -o name | grep causa-backend | head -1) -- \
  curl -s http://kubernetes-mcp-server.diagnostics-tool.svc.cluster.local:8080/healthz

# 3. Check ConfigMap has correct MCP endpoint
oc get configmap causa-config -n pinky -o yaml | grep MCP_K8S_ENDPOINT
# Should be: http://kubernetes-mcp-server.diagnostics-tool.svc.cluster.local:8080
```

**Solution:**
```bash
# Deploy MCP server
oc apply -f deployment/kubernetes/mcp-server.yaml -n diagnostics-tool

# Verify it's accessible
oc get svc -n diagnostics-tool kubernetes-mcp-server
```

---

### Issue 4: Bob Returns Conversational Text (With MCP Data)

**Symptoms:**
```
DEBUG: Extracted content: "I'll help you analyze..."
ERROR: JsonParseException: Unrecognized token 'I'
# BUT MCP context was collected successfully
```

**Diagnosis:**
```bash
# Check if ibm-bob template is being used
oc logs -n pinky $POD | grep "template"

# Check prompt template has forceful JSON instructions
grep -A 5 "ibm-bob:" src/main/resources/prompts/rca-prompt-template.yml
```

**Solution:**
```bash
# Ensure ibm-bob template has forceful instructions
# See src/main/resources/prompts/rca-prompt-template.yml lines 444-452

# Template should include:
# "CRITICAL: You MUST respond with ONLY valid JSON..."
# "Do not include ANY text before or after the JSON..."
```

---

### Issue 5: Build Failure - Ambiguous Dependencies

**Symptoms:**
```
ERROR: Ambiguous dependencies for type PromptSender
- BobShellPromptSender
- LangChainPromptSender
```

**Cause:** Both PromptSenders are being created

**Solution:**
```bash
# Ensure LangChainPromptSender has @UnlessBuildProperty
grep "@UnlessBuildProperty" src/main/java/com/causa/llm/LangChainPromptSender.java

# Should show:
# @UnlessBuildProperty(name = "causa.llm.provider", stringValue = "bob-shell")

# If missing, copy from bob-shell-integration branch
git checkout bob-shell-integration -- src/main/java/com/causa/llm/LangChainPromptSender.java
```

---

## Verification Commands

### Quick Health Check

```bash
# All-in-one verification
ROUTE="causa-backend-pinky.apps.cluster-ns4r6.ns4r6.sandbox151.opentlc.com"
POD=$(oc get pods -n pinky --no-headers | grep causa-backend | grep Running | head -1 | awk '{print $1}')

echo "=== Pod Status ==="
oc get pod -n pinky $POD

echo ""
echo "=== Bob Shell Logs ==="
oc logs -n pinky $POD | grep "BOB Shell"

echo ""
echo "=== Health Endpoint ==="
curl -k "https://$ROUTE/q/health/ready" | jq '.checks[] | select(.name=="llm")'

echo ""
echo "=== ConfigMap ==="
oc get configmap causa-config -n pinky -o jsonpath='{.data.LLM_PROVIDER}'
echo ""
oc get configmap causa-config -n pinky -o jsonpath='{.data.LLM_MODEL_NAME}'
echo ""
```

### Check Last RCA Generation

```bash
POD=$(oc get pods -n pinky --no-headers | grep causa-backend | grep Running | head -1 | awk '{print $1}')

# Get last alert processed
oc logs -n pinky $POD --tail=500 | grep "Alert accepted for processing" | tail -1

# Get last RCA result
oc logs -n pinky $POD --tail=500 | grep -E "RCA generated|issue_title" | tail -5

# Get last error (if any)
oc logs -n pinky $POD --tail=500 | grep "ERROR" | tail -5
```

### Compare with Claude/Vertex AI

```bash
# To compare Bob Shell vs Claude, run same test with different providers:

# 1. Test with Bob Shell (already deployed)
# 2. Switch to Vertex AI:
oc patch configmap causa-config -n pinky --type=merge -p '{"data":{"LLM_PROVIDER":"vertex-ai-anthropic"}}'
oc rollout restart deployment/causa-backend -n pinky

# 3. Trigger same alert
# 4. Compare results
```

---

## Test Matrix

| Test Case | MCP Server | Expected Behavior | Status |
|-----------|-----------|-------------------|--------|
| Health Check | Not Required | Bob returns "OK" | ✅ PASS |
| Alert without MCP | Down | Bob responds "no data" | ✅ PASS |
| Alert with MCP | Running | Bob returns JSON RCA | ⏳ Pending MCP |
| Local T1 Test | Not Required | Bob returns JSON RCA | ⏳ Not tested |
| Local T14 Test | Not Required | Bob returns JSON RCA | ⏳ Not tested |

---

## References

- **Aakriti's Successful Test:** `docs/BOB-SHELL-SUCCESS.md`
- **Deployment Status:** `BOB-SHELL-READY.md`
- **Test Contexts:** `src/main/resources/test-contexts/`
- **Bob Shell Integration PR:** #24
- **MCP Server Endpoint:** `http://kubernetes-mcp-server.diagnostics-tool.svc.cluster.local:8080`

---

## Next Actions

1. ✅ **Bob Shell Integration** - Complete
2. ✅ **Deployment to OpenShift** - Complete
3. ✅ **Health Check Verification** - Passed
4. ⏳ **Deploy MCP Server** - Required for full testing
5. ⏳ **Run End-to-End Test** - Pending MCP server
6. ⏳ **Compare with Claude Results** - After successful Bob test

---

**Last Updated:** 2026-06-25  
**Tested By:** Pinky Gupta  
**Status:** Bob Shell integration complete, ready for MCP server deployment
