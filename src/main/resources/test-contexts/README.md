# Test Contexts for RCA Generation

This directory contains test context files used by `DiagnosticServiceImpl.buildTestContext()` for internal testing until MCP integration is complete.

## Purpose

These files provide realistic diagnostic scenarios that mimic the output from MCP servers (Kubernetes, Kruize, Cryostat). They allow testing RCA generation without requiring actual MCP connections.

## Available Test Contexts

### heap-oom-scenario.txt
**Scenario**: Unbounded registry growth causing OOM

**Characteristics**:
- Pod: `heap-oom-prom-5785ff66b9-pt87l` in `chaos-test` namespace
- Memory Usage: 478/512 MiB (93.4% utilization)
- Issue: DiscoveryScheduler inserting targets without limit (95k → 115k in 30s)
- Evidence: 1 OutOfMemoryError, BackOff restart, 3.3s GC pause
- Expected RCA: `POSSIBLE_OOM_KILLED` with root cause = unbounded registry growth

**Signals Included**:
1. ✅ POD_STATUS - Running
2. ✅ KUBERNETES_EVENTS - BackOff, container lifecycle
3. ✅ PROMETHEUS_METRICS - CPU 0.071/0.5, Memory 478/512 MiB
4. ✅ KRUIZE_RECOMMENDATIONS - Increase memory to 948 MiB
5. ✅ POD_LOGS - Registry growth logs
6. ✅ JFR_CONTAINER_ANALYSIS - 512 MiB limit, cgroupv2
7. ✅ JFR_GC_ANALYSIS - DefNew, Allocation Failure, 3.3s pause
8. ✅ JFR_MEMORY_ANALYSIS - Heap 416 MiB, Non-heap 50 MiB
9. ✅ JFR_THREAD_ANALYSIS - 23 active threads
10. ✅ JFR_EXCEPTION_ANALYSIS - 1 OutOfMemoryError

**Source**: Based on [causa-prompts/master-prompt-with-signals.txt](https://github.com/shekhar316/causa-prompts/blob/main/master-prompt-with-signals.txt)

## Usage

```java
// In DiagnosticServiceImpl
private String buildTestContext(Alert alert) {
    try (InputStream is = getClass().getResourceAsStream("/test-contexts/heap-oom-scenario.txt")) {
        return new String(is.readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException e) {
        throw new RuntimeException("Failed to load test context", e);
    }
}
```

## Adding New Test Scenarios

To add a new test context:

1. Create a new `.txt` file in this directory
2. Follow the format with `## SECTION_NAME` headers
3. Include realistic timestamps, pod names, metrics
4. Document expected RCA outcome in this README
5. Update `buildTestContext()` to load the new file (or make it configurable)

## Future Work

When MCP integration is complete, replace `buildTestContext()` with:

```java
private String buildContextForLLM(Alert alert) {
    // Replace test context with real MCP data
    String contextString = mcpContextCollector.collectContextAsString(alert);
    return contextString;
}
```

These test files can be retained for unit/integration testing.
