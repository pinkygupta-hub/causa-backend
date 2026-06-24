# RCA Test Results - T1 & T14

**Date**: 2026-06-24  
**Image**: quay.io/pingupta/irb:rca-t1-t14-20260624-134450

---

## T1: Memory/OOM Case

**Anomaly**: POSSIBLE_OOM_KILLED ✅  
**RCA Confidence**: 0.82  
**Solution Confidence**: 0.78  

**Root Cause**: Unbounded registry growth in DiscoveryScheduler (95k→115k targets in 25s), exhausting 512 MiB limit. JVM triggers Serial GC (3.29s pause) but cannot reclaim memory. Container crashes (BackOff event) with java.lang.OutOfMemoryError.

**Solutions**:
1. Implement bounded registry with eviction policy (HIGH)
2. Increase memory to 948 MiB per Kruize (MEDIUM)
3. Replace Serial GC with G1GC (MEDIUM)

---

## T14: GC Pause Case

**Anomaly**: POSSIBLE_GC_PAUSE ✅  
**RCA Confidence**: 0.75  
**Solution Confidence**: 0.72  

**Root Cause**: Serial GC (DefNew) causing 3.29-second stop-the-world pause due to Allocation Failure. Memory pressure at 478/512 MiB (93.4%) triggers frequent GC. Single-CPU constraint limits GC parallelism.

**Solutions**:
1. Switch to G1GC or ZGC for concurrent collection (HIGH)
2. Increase heap size to 948 MiB per Kruize (MEDIUM)
3. Tune GC parameters for pause time goals (MEDIUM)

---

## Analysis

✅ **Correct Anomaly Detection**: T1 correctly identified memory issue, T14 correctly identified GC issue  
✅ **Different Confidence**: T1 (0.82) > T14 (0.75) - appropriate given T1 has OutOfMemoryError evidence  
✅ **Actionable Solutions**: Both provide specific, implementable fixes  
✅ **Evidence-Based**: Both cite specific JFR data, metrics, logs

**Files**: T1-rca.txt, T14-rca.txt
