#!/bin/bash

# Script to run all T1-T9 RCA tests
# Each test sends an alert with a specific name that triggers loading the corresponding context

ROUTE=$(oc get route -n pinky causa-backend -o jsonpath='{.spec.host}')
RESULTS_DIR="docs/tmp-pinky/test-results"

mkdir -p "$RESULTS_DIR"

echo "🚀 Starting T1-T9 RCA Tests"
echo "Route: $ROUTE"
echo "Results directory: $RESULTS_DIR"
echo ""

# Test scenarios
declare -A tests
tests[T1]="All data (6/6 signals)"
tests[T2]="Missing POD EVENTS (5/6 signals)"
tests[T6]="Missing JFR (5/6 signals)"
tests[T7]="Missing KRUIZE (5/6 signals)"
tests[T8]="Missing POD LOGS (5/6 signals)"
tests[T9]="Missing LOGS + EVENTS (4/6 signals)"

for test_name in T1 T2 T6 T7 T8 T9; do
    echo "========================================================================================================"
    echo "Running Test ${test_name}: ${tests[$test_name]}"
    echo "========================================================================================================"

    # Send alert
    curl -sk -X POST "https://$ROUTE/api/v1/webhooks/alerts" \
      -H "Content-Type: application/json" \
      -d "{
        \"alerts\": [{
          \"status\": \"firing\",
          \"labels\": {
            \"alertname\": \"HighMemoryUsage_${test_name}\",
            \"severity\": \"critical\",
            \"pod\": \"heap-oom-prom-test-${test_name}\",
            \"namespace\": \"test-namespace\",
            \"container\": \"heap-oom-prom\"
          },
          \"annotations\": {
            \"summary\": \"RCA Test ${test_name} - ${tests[$test_name]}\"
          }
        }]
      }" > /dev/null

    echo "✅ Alert sent for ${test_name}"
    echo "⏳ Waiting 70 seconds for RCA to complete..."
    sleep 70

    # Fetch logs
    echo "📥 Fetching RCA output..."
    oc logs -n pinky -l app.kubernetes.io/name=causa-backend --tail=500 > "${RESULTS_DIR}/${test_name}-full-log.txt"

    # Extract RCA summary
    grep -A 50 "PARSED RCA OUTPUT" "${RESULTS_DIR}/${test_name}-full-log.txt" > "${RESULTS_DIR}/${test_name}-rca-summary.txt" 2>/dev/null

    # Extract key metrics
    anomaly=$(grep "Anomaly Type:" "${RESULTS_DIR}/${test_name}-rca-summary.txt" | head -1 | cut -d':' -f2 | xargs)
    rca_conf=$(grep "RCA Confidence:" "${RESULTS_DIR}/${test_name}-rca-summary.txt" | head -1 | cut -d':' -f2 | xargs)
    sol_conf=$(grep "Solution Confidence:" "${RESULTS_DIR}/${test_name}-rca-summary.txt" | head -1 | cut -d':' -f2 | xargs)

    echo "📊 Results:"
    echo "   Anomaly Type: ${anomaly:-N/A}"
    echo "   RCA Confidence: ${rca_conf:-N/A}"
    echo "   Solution Confidence: ${sol_conf:-N/A}"
    echo ""

    # Save summary
    echo "${test_name},${anomaly:-N/A},${rca_conf:-N/A},${sol_conf:-N/A}" >> "${RESULTS_DIR}/all-tests-summary.csv"
done

echo ""
echo "========================================================================================================"
echo "✅ All tests completed!"
echo "========================================================================================================"
echo ""
echo "Results saved to: $RESULTS_DIR"
echo ""
echo "Quick Summary:"
echo "--------------"
cat "${RESULTS_DIR}/all-tests-summary.csv"
echo ""
echo "View full results:"
echo "  ls -lh ${RESULTS_DIR}/"
