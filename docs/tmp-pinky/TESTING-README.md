# RCA Testing - Complete Guide

**Purpose**: Test and validate RCA generation across 9 different scenarios (T1-T9) with varying signal availability.

**Test Source**: https://github.com/shekhar316/causa-prompts/tree/main/tests

**Branch**: rca-llm-integration (testing with debug logs and test contexts)

---

## 📋 Documentation Index

1. **[RCA-TESTING-PLAN.md](./RCA-TESTING-PLAN.md)** - Overview of all T1-T9 scenarios and expected results
2. **[TESTING-EXECUTION-GUIDE.md](./TESTING-EXECUTION-GUIDE.md)** - Step-by-step instructions for deployment and testing
3. **[RCA-CORRECTNESS-ANALYSIS-TEMPLATE.md](./RCA-CORRECTNESS-ANALYSIS-TEMPLATE.md)** - Template for analyzing RCA outputs

---

## 🚀 Quick Start

### Prerequisites
- OpenShift CLI (`oc`) installed and logged in
- Docker installed and logged into Quay
- Access to `pinky` namespace on OpenShift
- Java 21 and Maven installed

### Step 1: Deploy
```bash
cd /Users/pinkygupta/Documents/workspace/causa-backend
git checkout rca-llm-integration
./deploy-for-testing.sh
```

### Step 2: Download Test Contexts
```bash
# Manually download T1-T9 context files from:
# https://github.com/shekhar316/causa-prompts/tree/main/tests

# Save each context.txt as:
src/main/resources/test-contexts/t1-context.txt
src/main/resources/test-contexts/t2-context.txt
# ... through t9-context.txt
```

### Step 3: Update Code to Load Contexts
Modify `DiagnosticServiceImpl.buildTestContext()` to load different contexts based on alert name.

### Step 4: Run Tests
```bash
./test-rca-scenarios.sh
```

### Step 5: Analyze Results
Fill out `RCA-CORRECTNESS-ANALYSIS-TEMPLATE.md` with actual results from `docs/tmp-pinky/test-results/`

---

## 📊 Test Scenarios

| Test | Description | Signals Available | Expected Confidence |
|------|-------------|-------------------|---------------------|
| T1 | All data with raw JFR | 6/6 | 0.85-0.90 |
| T2 | Missing pod events | 5/6 (no events) | 0.70-0.80 |
| T3 | All data with JFR from MCP | 6/6 | 0.85-0.90 |
| T4 | Prompt reductions | 6/6 | 0.80-0.90 |
| T5 | Context reduction | 6/6 | 0.75-0.85 |
| T6 | Missing JFR | 5/6 (no JFR) | 0.65-0.75 |
| T7 | Missing Kruize | 5/6 (no Kruize) | 0.75-0.85 |
| T8 | Missing logs | 5/6 (no logs) | 0.60-0.70 |
| T9 | Missing logs & events | 4/6 (no logs, events) | 0.45-0.60 |

---

## ✅ Evaluation Criteria

### 1. Anomaly Detection (100% accuracy expected)
- All T1-T9 should detect: **POSSIBLE_OOM_KILLED** or **OOM_KILLED**
- Check `anomaly_type` field in RCA output

### 2. Confidence Calibration
- Confidence should **decrease** as signals are removed
- T1-T5 (full context): 0.80-0.90
- T6-T8 (missing 1 signal): 0.60-0.75
- T9 (missing 2 signals): 0.45-0.60

### 3. No Hallucinations
- Every piece of evidence MUST exist in the provided context
- Log quotes must be verbatim
- No invented metrics or events

### 4. Solution Quality
- At least 2 actionable solutions per test
- Solutions should address the identified root cause
- Specific solutions > generic solutions

### 5. Graceful Degradation
- When signals are missing, RCA should:
  - Mention missing signals in `llm_notes`
  - State uncertainty in `confidence_summary`
  - Lower confidence scores appropriately

---

## 📁 File Structure

```
docs/tmp-pinky/
├── TESTING-README.md (this file)
├── RCA-TESTING-PLAN.md
├── TESTING-EXECUTION-GUIDE.md
├── RCA-CORRECTNESS-ANALYSIS-TEMPLATE.md
└── test-results/
    ├── t1-full-log.txt
    ├── t1-rca-summary.txt
    ├── t1-context.txt
    ├── ... (repeat for t2-t9)
    └── SUMMARY.md

src/main/resources/test-contexts/
├── t1-context.txt
├── t2-context.txt
├── ... (t3-t9)
└── README.md

Scripts:
├── deploy-for-testing.sh
└── test-rca-scenarios.sh
```

---

## 🔍 Analysis Workflow

1. **Execute Tests** → Captures RCA outputs in `test-results/`
2. **Extract Data** → For each test:
   - Anomaly type
   - Confidence scores
   - Root cause
   - Evidence count
   - Solution count
3. **Verify Correctness** → Check against expected results
4. **Document Findings** → Fill out correctness analysis template
5. **Generate Summary** → Create final report with statistics

---

## 📈 Expected Patterns

### Good Patterns (✅)
- Confidence drops as signals are removed (T1 > T6 > T9)
- All evidences are verifiable in context
- Solutions cite specific evidence
- llm_notes narrates the analysis transparently
- Mentions missing signals when applicable

### Bad Patterns (❌)
- Confidence stays high even when signals are missing
- Evidence not found in context (hallucination)
- Generic solutions without justification
- Does not acknowledge missing signals
- Inconsistent anomaly detection

---

## 🛠️ Troubleshooting

### Test Failed to Execute
```bash
# Check deployment
oc get pods -n pinky -l app.kubernetes.io/name=causa-backend

# View logs
oc logs -n pinky -l app.kubernetes.io/name=causa-backend --tail=100
```

### RCA Output Not Captured
```bash
# Search logs manually
oc logs -n pinky -l app.kubernetes.io/name=causa-backend | grep -A 100 "PARSED RCA OUTPUT"
```

### Context File Not Loading
```bash
# Verify files are in the image
oc exec -n pinky deployment/causa-backend -- ls -la /deployments/app/classes/test-contexts/
```

---

## 📝 Deliverables

After completing testing:

1. ✅ **Completed Correctness Analysis** 
   - RCA-CORRECTNESS-ANALYSIS-TEMPLATE.md filled with actual results

2. ✅ **Test Results Archive**
   - All t1-t9 logs, summaries, and contexts saved

3. ✅ **Summary Statistics**
   - Anomaly detection accuracy
   - Confidence calibration analysis
   - Hallucination count
   - Solution quality metrics

4. ✅ **Recommendations**
   - Prompt improvements needed
   - Model behavior observations
   - Next steps

---

## 📊 Success Metrics

**Overall Pass Criteria**:
- ✅ 100% anomaly detection accuracy (9/9 correct)
- ✅ Confidence calibration: monotonic decrease as signals removed
- ✅ Zero critical hallucinations
- ✅ 90%+ solution quality (actionable and specific)
- ✅ Graceful degradation visible in T6-T9

**Grade Scale**:
- **A (90-100%)**: Excellent RCA quality, proper calibration, no hallucinations
- **B (80-89%)**: Good RCA quality, minor calibration issues, rare hallucinations
- **C (70-79%)**: Acceptable RCA quality, some calibration issues, occasional hallucinations
- **D (60-69%)**: Poor RCA quality, significant issues
- **F (<60%)**: Failing - major accuracy or hallucination problems

---

## 🔗 Related Links

- Test Source: https://github.com/shekhar316/causa-prompts/tree/main/tests
- Prompt Template: `src/main/resources/prompts/rca-prompt-template.yml`
- RCA Code: `src/main/java/com/causa/core/services/impl/DiagnosticServiceImpl.java`
- Deployment: `/tmp/causa-deploy-pinky/`

---

## ⏱️ Timeline

- **Setup**: 30 minutes
- **Testing**: 90 minutes (9 tests × 10 min)
- **Analysis**: 60-90 minutes
- **Total**: 3-4 hours

---

## 👥 Contact

For questions or issues, refer to the main project documentation or create a GitHub issue.

**Last Updated**: 2026-06-24
