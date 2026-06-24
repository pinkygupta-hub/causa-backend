#!/bin/bash

# Test RCA Generation Script

echo "======================================"
echo "Testing RCA Generation"
echo "======================================"
echo ""

# Check if app is running
if ! curl -s http://localhost:8080/q/health > /dev/null 2>&1; then
    echo "❌ Application is not running on localhost:8080"
    echo "Please start the app with: ./mvnw quarkus:dev"
    exit 1
fi

echo "✅ Application is running"
echo ""

# Create a test alert
echo "📤 Sending test alert..."
echo ""

RESPONSE=$(curl -s -X POST http://localhost:8080/api/v1/alerts \
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
  }')

echo "📥 Response:"
echo "$RESPONSE" | jq '.' 2>/dev/null || echo "$RESPONSE"
echo ""

# Extract alert ID
ALERT_ID=$(echo "$RESPONSE" | jq -r '.alertId' 2>/dev/null)

if [ -z "$ALERT_ID" ] || [ "$ALERT_ID" = "null" ]; then
    echo "❌ Failed to create alert"
    exit 1
fi

echo "✅ Alert created with ID: $ALERT_ID"
echo ""

# Check diagnostics
echo "🔍 Checking diagnostics..."
sleep 2

DIAGNOSTICS=$(curl -s "http://localhost:8080/api/v1/diagnostics?alertId=$ALERT_ID")
echo "$DIAGNOSTICS" | jq '.' 2>/dev/null || echo "$DIAGNOSTICS"
echo ""

echo "======================================"
echo "✅ Test Complete!"
echo "======================================"
echo ""
echo "Check application logs for RCA generation details:"
echo "  - Look for 'Building LLM context'"
echo "  - Look for 'RCA prompt built'"
echo "  - Look for 'RCA generated successfully'"
echo ""
