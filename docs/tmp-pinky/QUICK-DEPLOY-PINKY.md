# Quick Deploy to Pinky Namespace - Cheat Sheet

This is a condensed version of the complete deployment guide for quick reference.

## Prerequisites

- OpenShift CLI logged in
- Docker/Podman installed
- GCloud CLI authenticated
- Quay.io access

## Deployment Steps

### 1. Login & Create Namespace

```bash
oc login --server=https://api.cluster-ns4r6.ns4r6.sandbox151.opentlc.com:6443
oc new-project pinky || oc project pinky
```

### 2. Build & Push Image

```bash
cd /path/to/causa-backend
./mvnw clean package -DskipTests
docker build -f src/main/docker/Dockerfile.jvm --platform linux/amd64 -t quay.io/pingupta/irb:rca-latest .
podman push quay.io/pingupta/irb:rca-latest
```

### 3. Deploy Database

```bash
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
```

### 4. Deploy Application

```bash
# Create ConfigMap
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

# Create DB Secret
oc create secret generic causa-db-secret \
  --from-literal=CAUSA_DB_PASSWORD=causa_password \
  -n pinky

# Deploy Application
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
EOF
```

### 5. Create Route

```bash
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
    targetPort: 8080
  tls:
    termination: edge
EOF
```

### 6. Configure Vertex AI

```bash
# Authenticate
gcloud auth application-default login

# Run setup script
export GCP_PROJECT_ID="itpc-gcp-cp-pe-eng-claude"
./scripts/llm/setup-vertex-ai.sh --env openshift --project $GCP_PROJECT_ID

# Apply generated configs
cd deployment/kubernetes/vertex-ai/generated
sed -i 's/namespace: diagnostics-tool/namespace: pinky/g' *.yaml
oc apply -f causa-llm-secrets.yaml
oc apply -f gcp-adc-credentials.yaml

# Mount credentials
oc set env deployment/causa-backend -n pinky VERTEX_PROJECT_ID=itpc-gcp-cp-pe-eng-claude
oc patch deployment causa-backend -n pinky --patch-file deployment-adc-patch.yaml
oc rollout restart deployment/causa-backend -n pinky
```

### 7. (Optional) Remove Readiness Probe

```bash
oc patch deployment causa-backend -n pinky --type=json \
  -p='[{"op": "remove", "path": "/spec/template/spec/containers/0/readinessProbe"}]'
```

## Verification

```bash
# Check pods
oc get pods -n pinky

# Check logs
oc logs -n pinky -l app.kubernetes.io/name=causa-backend --tail=50 | grep -i "llm"

# Get URL
ROUTE_URL=$(oc get route -n pinky causa-backend -o jsonpath='{.spec.host}')
echo "https://$ROUTE_URL"

# Test health
curl -k "https://$ROUTE_URL/q/health/live"

# Test RCA
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
        "summary": "High memory usage detected"
      }
    }]
  }'

# Monitor RCA generation
oc logs -n pinky -l app.kubernetes.io/name=causa-backend -f | \
  grep -E "Building LLM context|RCA generated|anomalyType"
```

## Update Image After Rebuild

```bash
# After rebuilding and pushing new image
oc set image deployment/causa-backend -n pinky causa-backend=quay.io/pingupta/irb:rca-latest
oc rollout status deployment/causa-backend -n pinky
```

## Clean Up

```bash
oc delete all --all -n pinky
oc delete configmap causa-config -n pinky
oc delete secret causa-db-secret causa-llm-secrets gcp-adc-credentials -n pinky
oc delete project pinky
```

## Troubleshooting Quick Checks

```bash
# Check all resources
oc get all,cm,secret -n pinky

# Check pod status
oc describe pod -n pinky -l app.kubernetes.io/name=causa-backend

# Check logs
oc logs -n pinky -l app.kubernetes.io/name=causa-backend --tail=100

# Check database connectivity
oc exec deployment/causa-backend -n pinky -- \
  psql postgresql://causa_backend:causa_password@postgres.pinky.svc.cluster.local:5432/causa -c '\l'

# Check GCP credentials
oc exec deployment/causa-backend -n pinky -- ls -la /var/secrets/google/
oc exec deployment/causa-backend -n pinky -- env | grep -E "VERTEX|GOOGLE"

# Restart deployment
oc rollout restart deployment/causa-backend -n pinky
```

## See Also

- Complete guide: [RCA-DEPLOYMENT-COMPLETE.md](RCA-DEPLOYMENT-COMPLETE.md)
- Testing guide: [TESTING-RCA-GENERATION.md](TESTING-RCA-GENERATION.md)
- Vertex AI setup: [llm/vertex-ai-non-production-guide.md](llm/vertex-ai-non-production-guide.md)



---------
Trigger alert
 curl -k -s -X POST "https://causa-backend-pinky.apps.cluster-ns4r6.ns4r6.sandbox151.opentlc.com/api/v1/webhooks/alerts" \
     -H "Content-Type: application/json" \
     -d '{
       "alerts": [{
         "status": "firing",
         "labels": {
           "alertname": "DebugHighMemory",
           "severity": "critical",
           "pod": "debug-oom-pod",
           "namespace": "debug-ns",
           "container": "debug-container"
         },
         "annotations": {
           "summary": "Debug alert for RCA output capture"
         }
       }]
     }'

     oc logs -n pinky -l app.kubernetes.io/name=causa-backend --tail=20 | grep -i "filter\|severity\|namespace"

     oc exec -n pinky deployment/postgres -- psql -U causa_backend -d causa -c "SELECT alert_id, created_at FROM alerts ORDER BY created_at DESC LIMIT 3;"


     docker build -f src/main/docker/Dockerfile.jvm --platform linux/amd64 -t quay.io/pingupta/irb:rca-debug . 2>&1 | tail -15

     podman push quay.io/pingupta/irb:rca-debug 2>&1 | tail -10

     oc set image deployment/causa-backend -n pinky causa-backend=quay.io/pingupta/irb:rca-debug && oc rollout status deployment/causa-backend -n pinky --timeout=180s

Get the context sent to LLM
     oc logs -n pinky -l app.kubernetes.io/name=causa-backend --tail=500 --since=2m | grep -A 500 "=== CONTEXT SENT TO LLM ===" | head -300


oc logs -n pinky -l app.kubernetes.io/name=causa-backend --tail=50 | grep -E "test-container|RCA|LLM"
   Check recent logs for RCA processing