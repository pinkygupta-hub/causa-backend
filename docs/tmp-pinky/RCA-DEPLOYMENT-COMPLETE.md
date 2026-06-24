# RCA System - Complete Deployment Guide

## Overview
This document describes the complete implementation and deployment of the Root Cause Analysis (RCA) system on OpenShift, including YAML-based prompt templates, test context integration, and Vertex AI configuration.

**Status**: ✅ **Successfully deployed and tested on OpenShift**

**Deployment Date**: June 23, 2026  
**Namespace**: `pinky`  
**Cluster**: OpenShift (api.cluster-ns4r6.ns4r6.sandbox151.opentlc.com)  
**Image**: `quay.io/pingupta/irb:rca-latest`

---

## Architecture

### Components
1. **Causa Backend** - Java/Quarkus application with RCA logic
2. **PostgreSQL** - Database for storing alerts and diagnostics
3. **Vertex AI** - Claude Sonnet 4.6 via Google Cloud Vertex AI
4. **YAML Prompt Templates** - Model-specific prompt configurations

### Data Flow
```
Alert Webhook → AlertWebhookController → AlertService → DiagnosticService
    ↓
collectContext() → buildTestContext() → String context
    ↓
performRootCauseAnalysis()
    ├─ RcaPromptBuilder.getSystemPrompt() [from YAML]
    ├─ RcaPromptBuilder.buildPrompt(alert, context) [from YAML]
    ├─ LLMRequest.builder(userPrompt).systemPrompt(systemPrompt)
    ├─ PromptSender.send(llmRequest) → Vertex AI → Claude Sonnet 4.6
    ├─ parseRcaResponse(jsonText) → RootCauseAnalysis object
    └─ Return RCA with confidence scores
```

---

## Implementation Details

### 1. YAML-Based Prompt Templates

**Location**: `src/main/resources/prompts/rca-prompt-template.yml`

**Template Types**:
- `default` - Full detailed prompt for Claude, GPT-4, etc.
- `bob` - Concise prompt for IBM Bob/Granite models
- `ollama` - Compact prompt for local Ollama models

**Key Features**:
- Placeholder substitution: `{{alertName}}`, `{{severity}}`, `{{podName}}`, `{{namespace}}`, `{{containerName}}`, `{{context}}`
- Model auto-detection based on provider/model name
- Template caching for performance

### 2. Test Context Implementation

**Location**: `DiagnosticServiceImpl.buildTestContext()`

**Source**: Based on [causa-prompts/master-prompt-with-signals.txt](https://github.com/shekhar316/causa-prompts/blob/main/master-prompt-with-signals.txt)

**Test Scenario**:
- Pod: `heap-oom-prom-5785ff66b9-pt87l`
- Namespace: `chaos-test`
- Memory: 478/512 MiB (93% utilization)
- Issue: Unbounded registry growth (95k → 115k targets)
- Evidence: OutOfMemoryError, BackOff events, GC pressure

**Signals Included**:
1. POD_STATUS
2. KUBERNETES_EVENTS
3. PROMETHEUS_METRICS
4. POD_LOGS
5. JFR_CONTAINER_ANALYSIS
6. JFR_GC_ANALYSIS
7. JFR_MEMORY_ANALYSIS
8. JFR_THREAD_ANALYSIS
9. JFR_EXCEPTION_ANALYSIS
10. KRUIZE_RECOMMENDATIONS

### 3. New Files Created

```
src/main/java/com/causa/core/domain/RootCauseAnalysis.java
src/main/java/com/causa/core/services/RcaPromptBuilder.java
src/main/java/com/causa/core/services/PromptTemplateLoader.java
src/main/resources/prompts/rca-prompt-template.yml
```

### 4. Modified Files

```
src/main/java/com/causa/core/services/impl/DiagnosticServiceImpl.java
  - Added RcaPromptBuilder, PromptSender dependencies
  - Implemented buildTestContext() with realistic test data
  - Implemented performRootCauseAnalysis() with full LLM integration
  - Implemented parseRcaResponse() for JSON parsing
  - Removed determineDiagnosisType() (handled by prompt)

pom.xml
  - Added SnakeYAML 2.2 dependency

src/main/java/com/causa/mcp/McpContextCollector.java
  - Added collectContextAsString() method (placeholder for now)
```

---

## Build & Deployment Process

### Prerequisites

1. **OpenShift CLI**: `oc` command installed and configured
2. **Docker/Podman**: For building container images
3. **GCloud CLI**: For Vertex AI authentication
4. **Access**: OpenShift cluster login credentials
5. **Quay.io**: Repository access for pushing images

### Step 0: Login to OpenShift

```bash
# Login to OpenShift cluster
oc login --server=https://api.cluster-ns4r6.ns4r6.sandbox151.opentlc.com:6443

# Create namespace if it doesn't exist
oc new-project pinky || oc project pinky
```

### Step 1: Build Application

```bash
# Navigate to project root
cd /path/to/causa-backend

# Build JAR
./mvnw clean package -DskipTests

# Build Docker image for Linux/AMD64 (OpenShift requirement)
docker build -f src/main/docker/Dockerfile.jvm \
  --platform linux/amd64 \
  -t quay.io/pingupta/irb:rca-latest \
  -t quay.io/pingupta/irb:rca-20260623-1659 \
  .

# Push to Quay.io
podman push quay.io/pingupta/irb:rca-latest
```

**Build Status**: ✅ Success  
**Image Size**: ~380 MB  
**Platform**: linux/amd64

### Step 2: Deploy PostgreSQL

```bash
# Deploy PostgreSQL database
oc apply -f - <<EOF
apiVersion: apps/v1
kind: Deployment
metadata:
  name: postgres
  namespace: pinky
spec:
  replicas: 1
  selector:
    matchLabels:
      app: postgres
  template:
    metadata:
      labels:
        app: postgres
    spec:
      containers:
      - name: postgres
        image: postgres:15-alpine
        env:
        - name: POSTGRES_USER
          value: causa_backend
        - name: POSTGRES_PASSWORD
          value: causa_password
        - name: POSTGRES_DB
          value: causa
        ports:
        - containerPort: 5432
---
apiVersion: v1
kind: Service
metadata:
  name: postgres
  namespace: pinky
spec:
  selector:
    app: postgres
  ports:
  - port: 5432
    targetPort: 5432
EOF

# Wait for PostgreSQL to be ready
oc wait --for=condition=available --timeout=120s deployment/postgres -n pinky

# Verify PostgreSQL is running
oc get pods -n pinky -l app=postgres
```

### Step 3: Deploy Causa Backend (Initial)

```bash
# Create ConfigMap for application configuration
oc apply -f - <<EOF
apiVersion: v1
kind: ConfigMap
metadata:
  name: causa-config
  namespace: pinky
data:
  CAUSA_DB_URL: "jdbc:postgresql://postgres.pinky.svc.cluster.local:5432/causa"
  CAUSA_DB_USERNAME: "causa_backend"
  LLM_PROVIDER: "vertex-ai-anthropic"
  LLM_MODEL_NAME: "claude-sonnet-4-6"
  VERTEX_LOCATION: "us-east5"
EOF

# Create Secret for database password
oc create secret generic causa-db-secret \
  --from-literal=CAUSA_DB_PASSWORD=causa_password \
  -n pinky

# Deploy Causa Backend application
oc apply -f - <<EOF
apiVersion: apps/v1
kind: Deployment
metadata:
  name: causa-backend
  namespace: pinky
  labels:
    app.kubernetes.io/name: causa-backend
spec:
  replicas: 1
  selector:
    matchLabels:
      app.kubernetes.io/name: causa-backend
  template:
    metadata:
      labels:
        app.kubernetes.io/name: causa-backend
    spec:
      containers:
      - name: causa-backend
        image: quay.io/pingupta/irb:rca-latest
        ports:
        - containerPort: 8080
          name: http
        env:
        - name: CAUSA_DB_URL
          valueFrom:
            configMapKeyRef:
              name: causa-config
              key: CAUSA_DB_URL
        - name: CAUSA_DB_USERNAME
          valueFrom:
            configMapKeyRef:
              name: causa-config
              key: CAUSA_DB_USERNAME
        - name: CAUSA_DB_PASSWORD
          valueFrom:
            secretKeyRef:
              name: causa-db-secret
              key: CAUSA_DB_PASSWORD
        - name: LLM_PROVIDER
          valueFrom:
            configMapKeyRef:
              name: causa-config
              key: LLM_PROVIDER
        - name: LLM_MODEL_NAME
          valueFrom:
            configMapKeyRef:
              name: causa-config
              key: LLM_MODEL_NAME
        - name: VERTEX_LOCATION
          valueFrom:
            configMapKeyRef:
              name: causa-config
              key: VERTEX_LOCATION
        livenessProbe:
          httpGet:
            path: /q/health/live
            port: 8080
          initialDelaySeconds: 30
          periodSeconds: 10
---
apiVersion: v1
kind: Service
metadata:
  name: causa-backend
  namespace: pinky
spec:
  selector:
    app.kubernetes.io/name: causa-backend
  ports:
  - port: 8080
    targetPort: 8080
    name: http
EOF

# Wait for initial deployment
oc wait --for=condition=available --timeout=180s deployment/causa-backend -n pinky
```

### Step 4: Create Route for External Access

```bash
# Create OpenShift route
oc apply -f - <<EOF
apiVersion: route.openshift.io/v1
kind: Route
metadata:
  name: causa-backend
  namespace: pinky
spec:
  to:
    kind: Service
    name: causa-backend
  port:
    targetPort: http
  tls:
    termination: edge
    insecureEdgeTerminationPolicy: Redirect
EOF

# Get the route URL
ROUTE_URL=$(oc get route -n pinky causa-backend -o jsonpath='{.spec.host}')
echo "Application URL: https://$ROUTE_URL"

# Test health endpoint
curl -k "https://$ROUTE_URL/q/health/live"
```

### Step 5: Configure Vertex AI (Automated)

**Important**: This step configures Vertex AI for LLM integration. Without this, RCA generation will fail.

```bash
# Authenticate with Google Cloud
gcloud auth application-default login

# Set your GCP project ID
export GCP_PROJECT_ID="itpc-gcp-cp-pe-eng-claude"

# Run automated setup script
./scripts/llm/setup-vertex-ai.sh --env openshift --project $GCP_PROJECT_ID
```

**What the script does**:
1. Validates GCP project and API access
2. Enables Vertex AI API (if not already enabled)
3. Grants `aiplatform.user` role to your account
4. Extracts ADC credentials from `~/.config/gcloud/application_default_credentials.json`
5. Creates Kubernetes secret `gcp-adc-credentials` with credentials
6. Creates Kubernetes secret `causa-llm-secrets` with project ID
7. Generates `deployment-adc-patch.yaml` for mounting credentials
8. Generates `apply.sh` script for easy application

**Expected Output**:
```
✅ Vertex AI setup completed successfully
📁 Generated files in: deployment/kubernetes/vertex-ai/generated/
   - causa-llm-secrets.yaml
   - gcp-adc-credentials.yaml
   - deployment-adc-patch.yaml
   - apply.sh
```

### Step 6: Apply Vertex AI Configuration

```bash
# Navigate to generated directory
cd deployment/kubernetes/vertex-ai/generated

# Update namespace from diagnostics-tool to pinky (script uses default namespace)
sed -i 's/namespace: diagnostics-tool/namespace: pinky/g' *.yaml

# Apply GCP secrets
oc apply -f causa-llm-secrets.yaml
oc apply -f gcp-adc-credentials.yaml

# Verify secrets were created
oc get secrets -n pinky | grep -E "causa-llm-secrets|gcp-adc-credentials"

# Set Vertex AI project ID as environment variable
oc set env deployment/causa-backend -n pinky \
  VERTEX_PROJECT_ID=itpc-gcp-cp-pe-eng-claude

# Patch deployment to mount GCP credentials
oc patch deployment causa-backend -n pinky \
  --patch-file deployment-adc-patch.yaml

# Restart deployment to apply changes
oc rollout restart deployment/causa-backend -n pinky
oc rollout status deployment/causa-backend -n pinky --timeout=180s
```

### Step 7: Update Application Image (if rebuilding)

**Note**: Only run this step if you rebuilt the application image. Otherwise skip to verification.

```bash
# Update deployment with new image
oc set image deployment/causa-backend -n pinky \
  causa-backend=quay.io/pingupta/irb:rca-latest

# Wait for rollout
oc rollout status deployment/causa-backend -n pinky
```

### Step 8: Remove Readiness Probe (Optional - for testing)

**Why**: The readiness probe includes LLM health checks. During testing, if LLM is slow to initialize, this can block pod startup.

```bash
# Remove readiness probe temporarily
oc patch deployment causa-backend -n pinky --type=json \
  -p='[{"op": "remove", "path": "/spec/template/spec/containers/0/readinessProbe"}]'

# Wait for rollout
oc rollout status deployment/causa-backend -n pinky
```

**Important**: Re-enable readiness probe for production deployments.

---

## Complete Deployment Summary

After following all steps, you should have:

1. ✅ **Namespace**: `pinky` project created
2. ✅ **PostgreSQL**: Database running at `postgres.pinky.svc.cluster.local:5432`
3. ✅ **Causa Backend**: Application deployed with 1+ replicas
4. ✅ **ConfigMap**: `causa-config` with database and LLM settings
5. ✅ **Secrets**: 
   - `causa-db-secret` - Database password
   - `causa-llm-secrets` - Vertex AI project ID
   - `gcp-adc-credentials` - Google Cloud credentials
6. ✅ **Service**: `causa-backend` ClusterIP service on port 8080
7. ✅ **Route**: External HTTPS access via OpenShift route
8. ✅ **LLM Integration**: Vertex AI Anthropic configured with Claude Sonnet 4.6

**Check deployment status**:
```bash
# All resources in pinky namespace
oc get all -n pinky

# Should show:
# - deployment.apps/postgres (1/1 ready)
# - deployment.apps/causa-backend (1/1 ready)
# - service/postgres
# - service/causa-backend
# - route.route.openshift.io/causa-backend
```

---

## Verification & Testing

### 1. Check Pod Status

```bash
# Check all pods are running
oc get pods -n pinky

# Expected output:
# NAME                             READY   STATUS    RESTARTS   AGE
# postgres-xxxxxxxxxx-xxxxx        1/1     Running   0          10m
# causa-backend-xxxxxxxxxx-xxxxx   1/1     Running   0          5m
```

### 2. Check LLM Connectivity

```bash
# Check application logs for LLM initialization
oc logs -n pinky -l app.kubernetes.io/name=causa-backend --tail=50 | grep -i "llm"
```

**Expected Output**:
```
✅ LLM connectivity verified | provider="vertex-ai-anthropic", model="claude-sonnet-4-6"
✅ LLM ready | provider="vertex-ai-anthropic", model="claude-sonnet-4-6"
```

If you see errors, check:
- GCP credentials are mounted: `oc exec deployment/causa-backend -n pinky -- ls -la /var/secrets/google/`
- Environment variables: `oc exec deployment/causa-backend -n pinky -- env | grep -E "VERTEX|GOOGLE"`

### 3. Test Health Endpoints

```bash
# Get route URL
ROUTE_URL=$(oc get route -n pinky causa-backend -o jsonpath='{.spec.host}')

# Test liveness
curl -k "https://$ROUTE_URL/q/health/live" | jq '.'

# Test readiness (if probe not removed)
curl -k "https://$ROUTE_URL/q/health/ready" | jq '.'
```

### 4. Test RCA Generation

```bash
# Get route URL
ROUTE_URL=$(oc get route -n pinky causa-backend -o jsonpath='{.spec.host}')

# Send test alert to trigger RCA generation
curl -k -X POST "https://$ROUTE_URL/api/v1/webhooks/alerts" \
  -H "Content-Type: application/json" \
  -d '{
    "alerts": [{
      "status": "firing",
      "labels": {
        "alertname": "HighMemoryUsage",
        "severity": "critical",
        "pod": "heap-oom-prom-5785ff66b9-pt87l",
        "namespace": "chaos-test",
        "container": "heap-oom-prom"
      },
      "annotations": {
        "summary": "High memory usage detected - 93% utilization"
      }
    }]
  }' | jq '.'
```

**Expected Response**:
```json
{
  "status": "accepted",
  "message": "All 1 alerts accepted and diagnostics initiated",
  "totalReceived": 1,
  "totalAccepted": 1,
  "acceptedAlertIds": ["heap-oom-prom-1782216248657"],
  "diagnosticIds": ["diag-heap-oom-prom-1782216248657-1782216248783"]
}
```

### 5. Monitor RCA Generation

```bash
oc logs -n pinky -l app.kubernetes.io/name=causa-backend -f | \
  grep -E "Building LLM context|RCA prompt built|LLM response|RCA generated|anomalyType"
```

**Expected Logs**:
```
INFO Building LLM context | alertId="heap-oom-prom-1782216248657"
INFO RCA prompt built | alertId="heap-oom-prom-1782216248657", systemPromptLength=241, userPromptLength=9648
INFO Sending prompt to LLM | provider="vertex-ai-anthropic", model="claude-sonnet-4-6"
INFO LLM response received | alertId="heap-oom-prom-1782216248657", modelUsed="claude-sonnet-4-6", inputTokens=3344, outputTokens=2851, latencyMs=57197
INFO RCA generated successfully | alertId="heap-oom-prom-1782216248657", anomalyType=POSSIBLE_OOM_KILLED, rcaConfidence=0.88, solutionConfidence=0.82
```

---

## Test Results

### Successful RCA Generation (June 23, 2026)

**Test Case**: High Memory Usage OOM Scenario

**Input**:
- Alert: HighMemoryUsage (critical)
- Pod: heap-oom-prom-5785ff66b9-pt87l
- Namespace: chaos-test
- Context: 93% memory, registry growth, OutOfMemoryError

**Output**:
- **Anomaly Type**: `POSSIBLE_OOM_KILLED` ✅
- **Root Cause Confidence**: 88% (very high) ✅
- **Solution Confidence**: 82% (high) ✅
- **Processing Time**: 57 seconds
- **Tokens**: 3,344 input + 2,851 output = 6,195 total

**Analysis Quality**: ✅ Excellent
- Correctly identified OOM scenario
- High confidence scores indicate strong evidence
- Appropriate categorization (POSSIBLE_OOM_KILLED vs OOM_KILLED)

---

## Configuration Details

### Deployment Configuration

**Namespace**: `pinky`  
**Replicas**: 2  
**Image**: `quay.io/pingupta/irb:rca-latest`

**Environment Variables**:
```yaml
LLM_PROVIDER: vertex-ai-anthropic
LLM_MODEL_NAME: claude-sonnet-4-6
VERTEX_PROJECT_ID: itpc-gcp-cp-pe-eng-claude
VERTEX_LOCATION: us-east5
CAUSA_DB_URL: jdbc:postgresql://postgres.pinky.svc.cluster.local:5432/causa
GOOGLE_APPLICATION_CREDENTIALS: /var/secrets/google/application_default_credentials.json
```

**Secrets**:
- `causa-llm-secrets` - Contains VERTEX_PROJECT_ID
- `gcp-adc-credentials` - Contains Google Application Default Credentials
- `causa-db-secret` - Contains database credentials

**Volume Mounts**:
```yaml
- name: gcp-credentials
  mountPath: /var/secrets/google
  secret: gcp-adc-credentials
```

### Routes

**External URL**: https://causa-backend-pinky.apps.cluster-ns4r6.ns4r6.sandbox151.opentlc.com

**Endpoints**:
- `/api/v1/webhooks/alerts` - POST - Receive alerts
- `/q/health/live` - GET - Liveness probe
- `/q/health/ready` - GET - Readiness probe (includes LLM check)

---

## Resource Usage

### Token Usage (per RCA)
- **Input**: ~3,300 tokens (prompt + context)
- **Output**: ~2,800 tokens (RCA JSON)
- **Total**: ~6,100 tokens per analysis

### Latency
- **LLM Call**: ~50-60 seconds
- **Total Request**: ~60-70 seconds (including context building and parsing)

### Cost Estimation (Vertex AI Anthropic)
- Input: 3,300 tokens × $3.00/million = $0.0099
- Output: 2,800 tokens × $15.00/million = $0.042
- **Total per RCA**: ~$0.052 (5.2 cents)

---

## Switching to Production

### Current Setup (Development/Testing)
- ✅ Application Default Credentials (ADC)
- ✅ Personal Google account
- ⚠️ Token expires after 1 hour
- ⚠️ Not suitable for production

### For Production Deployment

**Follow**: [Vertex AI Production Guide](llm/vertex-ai-production-guide.md)

**Key Changes Needed**:
1. Use GCP Service Account (not personal credentials)
2. Configure Workload Identity or service account key
3. Set up proper IAM roles
4. Use production-grade PostgreSQL (CloudNativePG operator)
5. Configure resource limits and autoscaling
6. Enable monitoring and alerting
7. Set up log aggregation
8. Configure backup and disaster recovery

---

## Troubleshooting

### LLM Not Ready

**Symptoms**: `LLM chat model not available` error

**Solutions**:
1. Check credentials are mounted:
   ```bash
   oc exec deployment/causa-backend -n pinky -- ls -la /var/secrets/google/
   ```

2. Verify environment variable:
   ```bash
   oc exec deployment/causa-backend -n pinky -- env | grep GOOGLE_APPLICATION_CREDENTIALS
   ```

3. Check logs for authentication errors:
   ```bash
   oc logs -n pinky -l app.kubernetes.io/name=causa-backend | grep -i "error\|failed"
   ```

4. Refresh ADC credentials (they expire after 1 hour):
   ```bash
   gcloud auth application-default login
   ./scripts/llm/setup-vertex-ai.sh --env openshift --project $GCP_PROJECT_ID
   cd deployment/kubernetes/vertex-ai/generated
   ./apply.sh
   ```

### Database Connection Issues

**Symptoms**: `password authentication failed` or `connection refused`

**Solutions**:
1. Check PostgreSQL is running:
   ```bash
   oc get pods -n pinky -l app=postgres
   ```

2. Verify database URL in ConfigMap:
   ```bash
   oc get configmap causa-config -n pinky -o jsonpath='{.data.CAUSA_DB_URL}'
   ```

3. Test connection from pod:
   ```bash
   oc exec deployment/causa-backend -n pinky -- \
     psql postgresql://causa_backend:causa_password@postgres.pinky.svc.cluster.local:5432/causa -c '\l'
   ```

### Image Pull Errors

**Symptoms**: `ImagePullBackOff` or `ErrImagePull`

**Solutions**:
1. Verify image exists in Quay.io:
   ```bash
   curl -s https://quay.io/api/v1/repository/pingupta/irb/tag/rca-latest
   ```

2. Check image pull secrets if repository is private

3. Ensure image was built for correct platform (linux/amd64):
   ```bash
   docker inspect quay.io/pingupta/irb:rca-latest | grep Architecture
   ```

---

## Next Steps

### Immediate (Complete RCA Flow)
1. ✅ Build YAML-based prompts - DONE
2. ✅ Implement LLM integration - DONE
3. ✅ Parse JSON to RootCauseAnalysis - DONE
4. ✅ Deploy to OpenShift - DONE
5. ✅ Test with Vertex AI - DONE
6. ⬜ Store RCA results in database - TODO
7. ⬜ Add API endpoint to retrieve RCA for an alert - TODO
8. ⬜ Implement validation engine - TODO

### Short-term (Enhanced Context)
1. ⬜ Replace test context with real MCP integration
2. ⬜ Add Prometheus metrics collection
3. ⬜ Integrate Cryostat JFR analysis
4. ⬜ Add Kruize recommendations
5. ⬜ Implement context caching

### Medium-term (Production Ready)
1. ⬜ Switch to service account authentication
2. ⬜ Deploy CloudNativePG for PostgreSQL
3. ⬜ Add monitoring and alerting
4. ⬜ Implement retry logic and circuit breakers
5. ⬜ Add rate limiting
6. ⬜ Set up cost tracking
7. ⬜ Create runbooks

### Long-term (Advanced Features)
1. ⬜ Multi-model support (compare Claude vs GPT-4)
2. ⬜ A/B testing different prompts
3. ⬜ RCA quality feedback loop
4. ⬜ Historical RCA analysis
5. ⬜ Automated remediation suggestions
6. ⬜ Integration with ticketing systems

---

## References

- **Source Code**: https://github.com/causaai/causa-backend
- **Prompt Templates**: https://github.com/shekhar316/causa-prompts
- **Deployment Docs**: [deployment/deployment.md](deployment/deployment.md)
- **Vertex AI Setup**: [llm/vertex-ai-non-production-guide.md](llm/vertex-ai-non-production-guide.md)
- **PostgreSQL Setup**: [deployment/postgres.md](deployment/postgres.md)

---

## Contributors

- Pinky Gupta - Implementation & Deployment
- Claude (Sonnet 4.5) - Code assistance and documentation

**Last Updated**: June 23, 2026
