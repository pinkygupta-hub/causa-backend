#!/bin/bash

# RCA Testing Script for T1-T9 Scenarios
# Tests all scenarios and captures RCA outputs

set -e

ROUTE_URL=$(oc get route -n pinky causa-backend -o jsonpath='{.spec.host}')
OUTPUT_DIR="./docs/tmp-pinky/test-results"
TIMESTAMP=$(date +%Y%m%d_%H%M%S)

# Create output directory
mkdir -p "$OUTPUT_DIR"

echo "======================================"
echo "RCA T1-T9 Scenario Testing"
echo "======================================"
echo ""
echo "Route: https://$ROUTE_URL"
echo "Output: $OUTPUT_DIR"
echo "Timestamp: $TIMESTAMP"
echo ""

# Function to test a scenario
test_scenario() {
    local test_num=$1
    local test_name=$2
    local pod_name="test-pod-t${test_num}"

    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    echo "Testing T${test_num}: ${test_name}"
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    echo ""

    # Trigger alert
    echo "1️⃣  Triggering alert for T${test_num}..."
    RESPONSE=$(curl -k -s -X POST "https://$ROUTE_URL/webhook/alertmanager" \
      -H "Content-Type: application/json" \
      -d "{
        \"alerts\": [{
          \"status\": \"firing\",
          \"labels\": {
            \"alertname\": \"HighMemoryUsage_T${test_num}\",
            \"severity\": \"critical\",
            \"pod\": \"${pod_name}\",
            \"namespace\": \"test-namespace\",
            \"container\": \"test-container\"
          },
          \"annotations\": {
            \"summary\": \"Test scenario T${test_num}: ${test_name}\"
          }
        }]
      }")

    echo "Response: $RESPONSE"
    echo ""

    # Wait for RCA generation
    echo "2️⃣  Waiting for RCA generation (60 seconds)..."
    sleep 60

    # Capture logs
    echo "3️⃣  Capturing RCA output from logs..."
    oc logs -n pinky -l app.kubernetes.io/name=causa-backend --tail=300 > "$OUTPUT_DIR/t${test_num}-full-log.txt"

    # Extract RCA output
    grep -A 100 "PARSED RCA OUTPUT" "$OUTPUT_DIR/t${test_num}-full-log.txt" | \
        grep -B 100 "========================================" | \
        head -n -1 > "$OUTPUT_DIR/t${test_num}-rca-summary.txt" 2>/dev/null || echo "RCA output not found in logs"

    # Extract context sent to LLM
    grep -A 200 "CONTEXT SENT TO LLM" "$OUTPUT_DIR/t${test_num}-full-log.txt" | \
        grep -B 200 "========== SYSTEM PROMPT ==========" | \
        head -n -1 > "$OUTPUT_DIR/t${test_num}-context.txt" 2>/dev/null || echo "Context not found in logs"

    echo "✅ Test T${test_num} complete"
    echo ""
    echo "Files saved:"
    echo "  - $OUTPUT_DIR/t${test_num}-full-log.txt"
    echo "  - $OUTPUT_DIR/t${test_num}-rca-summary.txt"
    echo "  - $OUTPUT_DIR/t${test_num}-context.txt"
    echo ""

    # Short delay between tests
    echo "⏸️  Waiting 10 seconds before next test..."
    sleep 10
    echo ""
}

# Check if OpenShift is accessible
echo "Checking OpenShift connectivity..."
oc whoami &>/dev/null || { echo "Error: Not logged into OpenShift"; exit 1; }
echo "✅ Connected to OpenShift as: $(oc whoami)"
echo ""

# Check if route exists
if [ -z "$ROUTE_URL" ]; then
    echo "Error: causa-backend route not found in pinky namespace"
    exit 1
fi
echo "✅ Found route: https://$ROUTE_URL"
echo ""

# Test health endpoint
echo "Testing health endpoint..."
HEALTH=$(curl -k -s "https://$ROUTE_URL/q/health/live" | jq -r '.status' 2>/dev/null)
if [ "$HEALTH" != "UP" ]; then
    echo "Warning: Health check returned: $HEALTH"
else
    echo "✅ Health check: UP"
fi
echo ""

# Confirm before proceeding
read -p "Ready to run all T1-T9 tests? This will take ~15 minutes. (y/N): " confirm
if [ "$confirm" != "y" ] && [ "$confirm" != "Y" ]; then
    echo "Aborted."
    exit 0
fi
echo ""

# Run all test scenarios
test_scenario "1" "Normal - All Data with Raw JFR"
test_scenario "2" "Missing Pod Events"
test_scenario "3" "Normal - All Data with JFR from MCP"
test_scenario "4" "Normal - Prompt Reductions"
test_scenario "5" "Normal - Context Reduction"
test_scenario "6" "Normal - Missing JFR"
test_scenario "7" "Normal - Missing Kruize Recommendations"
test_scenario "8" "Normal - Missing Logs"
test_scenario "9" "Normal - Missing Logs and Events"

echo "======================================"
echo "All Tests Complete!"
echo "======================================"
echo ""
echo "Results saved to: $OUTPUT_DIR"
echo ""
echo "Next steps:"
echo "1. Review RCA outputs: ls -la $OUTPUT_DIR"
echo "2. Analyze correctness and create summary document"
echo "3. Compare confidence scores across scenarios"
echo ""

# Generate summary
echo "Generating summary..."
echo "# RCA Test Results Summary - $TIMESTAMP" > "$OUTPUT_DIR/SUMMARY.md"
echo "" >> "$OUTPUT_DIR/SUMMARY.md"
echo "## Test Execution" >> "$OUTPUT_DIR/SUMMARY.md"
echo "" >> "$OUTPUT_DIR/SUMMARY.md"

for i in {1..9}; do
    echo "### T${i}" >> "$OUTPUT_DIR/SUMMARY.md"
    if [ -f "$OUTPUT_DIR/t${i}-rca-summary.txt" ]; then
        echo '```' >> "$OUTPUT_DIR/SUMMARY.md"
        cat "$OUTPUT_DIR/t${i}-rca-summary.txt" >> "$OUTPUT_DIR/SUMMARY.md"
        echo '```' >> "$OUTPUT_DIR/SUMMARY.md"
    else
        echo "❌ No output captured" >> "$OUTPUT_DIR/SUMMARY.md"
    fi
    echo "" >> "$OUTPUT_DIR/SUMMARY.md"
done

echo "✅ Summary generated: $OUTPUT_DIR/SUMMARY.md"
echo ""
