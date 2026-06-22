---
name: rca-validation
description: Validates Root Cause Analysis statements using assertion-based evidence analysis. Decomposes RCA claims into testable assertions, extracts evidence citations from logs/metrics, and provides confidence-scored verdicts.
compatibility: Requires PromptSender (LLM access) and ValidationContext (pod logs, K8s events, metrics, recommendations)
metadata:
  primary_tools: LLMAssertionGenerator, LLMEvidenceExtractor, LLMAssertionValidator
  use_case: rca-verification, assertion-validation, evidence-based-diagnostics
  version: 1.0
  package: com.causa.validation
---

# RCA Validation Skill

## Overview

Validates Root Cause Analysis statements through a 3-phase LLM-powered workflow:
1. **Assertion Generation** - Decomposes RCA into independent testable claims
2. **Evidence Extraction** - Searches logs/metrics for exact citations supporting/contradicting each assertion
3. **Assertion Validation** - Evaluates evidence quality and assigns confidence scores

**Anti-hallucination**: Evidence-only validation, explicit missing evidence detection, skeptical defaults.

## When to Use

- **After RCA generation** - Validate generated root cause before presenting to users
- **Manual RCA review** - Verify engineer-written RCA against available logs
- **Quality assurance** - Ensure RCA claims are evidence-backed, not inferred
- **Confidence scoring** - Provide quantitative confidence metrics (0-100%)
- **Evidence gap detection** - Identify claims with missing or weak evidence

## 3-Phase Validation Flow

### Phase 1: Assertion Generation

**Component**: `LLMAssertionGenerator`

**Purpose**: Break RCA statement into 2-5 independent, testable assertions

**Input**:
```java
AssertionGenerationRequest request = new AssertionGenerationRequest(rcaStatement);
```

**LLM Configuration**:
- Temperature: 0.3 (deterministic decomposition)
- System Prompt: "Expert diagnostic assertion decomposer"
- Output: JSON array of assertions

**Output Schema**:
```java
AssertionGenerationResult {
  List<ValidationAssertion> assertions;
}

ValidationAssertion {
  String claim;                              // The specific claim
  String validationQuestion;                 // How to verify this claim
  String supportingEvidenceDescription;      // What would prove it
  String contradictingEvidenceDescription;   // What would disprove it
  List<String> searchKeywords;               // Keywords for log search
}
```

**Example**:
```
RCA: "Unbounded memory growth from 166,350 targets caused OOM"
  ↓
Assertions:
  1. "Application crashed due to Java heap OutOfMemoryError"
  2. "166,350 targets loaded into memory"
  3. "Serial GC caused inefficient memory reclamation (597ms pauses)"
```

---

### Phase 2: Evidence Extraction

**Component**: `LLMEvidenceExtractor`

**Purpose**: Search logs/events/metrics for exact citations (per assertion)

**Input**:
```java
EvidenceExtractionRequest request = new EvidenceExtractionRequest(
  assertion,
  validationContext  // pod logs, K8s events, metrics, recommendations
);
```

**LLM Configuration**:
- Temperature: 0.2 (precise extraction)
- System Prompt: "Precise evidence citation extractor"
- Output: JSON with citations

**Output Schema**:
```java
EvidenceExtractionResult {
  List<EvidenceCitation> supportingEvidence;
  List<EvidenceCitation> contradictingEvidence;
  String missingEvidenceNote;  // Explicitly state missing data
}

EvidenceCitation {
  String sourceType;   // POD_LOG, K8S_EVENT, RESOURCE_USAGE, etc.
  String sourceName;
  String timestamp;
  String excerpt;      // Exact log line or metric value
}
```

**Example**:
```
Assertion: "Application crashed due to Java heap OutOfMemoryError"
  ↓
Supporting Evidence:
  - [POD_LOG] "Aborting due to java.lang.OutOfMemoryError: Java heap space"
  - [POD_LOG] "fatal error: OutOfMemory encountered: Java heap space"

Missing Evidence:
  - GC pause time data (would need JFR_DATA source)
```

---

### Phase 3: Assertion Validation

**Component**: `LLMAssertionValidator`

**Purpose**: Evaluate evidence quality and assign verdict (per assertion)

**Input**:
```java
AssertionValidationRequest request = new AssertionValidationRequest(
  assertion,
  evidenceExtractionResult
);
```

**LLM Configuration**:
- Temperature: 0.1 (very consistent judgments)
- System Prompt: "Skeptical evidence validator - default to UNSUPPORTED if uncertain"
- Output: JSON with verdict

**Output Schema**:
```java
AssertionValidationResult {
  AssertionValidationStatus status;  // SUPPORTED, WEAKLY_SUPPORTED, UNSUPPORTED
  int confidence;                    // 0-100%
  String reasoning;                  // Why this verdict
}
```

**Confidence Calibration**:
- **90-100%**: Overwhelming, unambiguous evidence (exact log matches)
- **70-89%**: Strong evidence, minor gaps
- **50-69%**: Moderate evidence, some uncertainty
- **30-49%**: Weak evidence or mixed signals
- **0-29%**: Insufficient or contradictory evidence

**Example**:
```
Assertion: "Application crashed due to Java heap OutOfMemoryError"
Evidence: 2 explicit log lines showing OOM crash
  ↓
Verdict: SUPPORTED (95% confidence)
Reasoning: "EXPLICIT evidence found. Exact line: 'Aborting due to 
           java.lang.OutOfMemoryError: Java heap space'"
```

---

## Usage Examples

### Basic Usage (Java)

```java
// Setup (inject via CDI)
@Inject LLMAssertionGenerator assertionGenerator;
@Inject LLMEvidenceExtractor evidenceExtractor;
@Inject LLMAssertionValidator validator;

// Phase 1: Generate assertions
AssertionGenerationRequest genReq = new AssertionGenerationRequest(rca);
AssertionGenerationResult genResult = assertionGenerator.generateAssertions(genReq);

// Phase 2 & 3: Extract evidence and validate each assertion
for (ValidationAssertion assertion : genResult.assertions()) {
    // Extract evidence
    EvidenceExtractionRequest evidenceReq = new EvidenceExtractionRequest(
        assertion, validationContext
    );
    EvidenceExtractionResult evidence = evidenceExtractor.extractEvidence(evidenceReq);
    
    // Validate
    AssertionValidationRequest validationReq = new AssertionValidationRequest(
        assertion, evidence
    );
    AssertionValidationResult validation = validator.validate(validationReq);
    
    System.out.println(assertion.claim() + ": " + validation.status() + 
                       " (" + validation.confidence() + "%)");
}
```

### Run Demo

```bash
# Compile and run demo showing actual LLM calls
./mvnw compile test-compile -q && \
java -cp "target/classes:target/test-classes:$(./mvnw dependency:build-classpath -q -Dmdep.outputFile=/dev/stdout)" \
com.causa.validation.BobValidationDemo
```

**Output**: Shows 5 LLM calls (1 decompose + 2 extract + 2 validate)

---

## Available Implementations

### LLM-Powered (Production)

**File**: `src/main/java/com/causa/validation/LLMAssertionGenerator.java`
- Calls `promptSender.send()` with temp=0.3
- Returns 2-5 assertions as JSON
- Parses with `extractJson()` helper

**File**: `src/main/java/com/causa/validation/evidence/LLMEvidenceExtractor.java`
- Calls `promptSender.send()` with temp=0.2
- Returns citations with exact timestamps and excerpts
- Includes `missingEvidenceNote` field

**File**: `src/main/java/com/causa/validation/evaluation/LLMAssertionValidator.java`
- Calls `promptSender.send()` with temp=0.1
- Returns status, confidence, reasoning
- Defaults to UNSUPPORTED if uncertain

### Fallback (Testing)

**File**: `src/main/java/com/causa/validation/DeterministicAssertionGenerator.java`
- Simple fallback - treats entire RCA as single assertion
- Does NOT decompose (use LLM version for real decomposition)

**File**: `src/test/java/com/causa/validation/evidence/MockEvidenceExtractor.java`
**File**: `src/test/java/com/causa/validation/evaluation/MockAssertionValidator.java`
- Mock implementations for testing without LLM

---

## Best Practices

1. **Use LLM implementations in production** - Don't use Deterministic/Mock implementations
2. **Provide complete ValidationContext** - More logs = better evidence extraction
3. **Review WEAKLY_SUPPORTED results** - Identify evidence gaps for investigation
4. **Aggregate confidences** - Overall RCA confidence = weighted average of assertions
5. **Log all LLM calls** - Track token usage, latency, cost
6. **Handle missing evidence gracefully** - `missingEvidenceNote` tells you what's absent

### Parallelization

Evidence extraction and validation can run in parallel per assertion:
```java
// Extract evidence for all assertions in parallel
List<CompletableFuture<EvidenceExtractionResult>> evidenceFutures = 
    assertions.stream()
        .map(a -> CompletableFuture.supplyAsync(() -> 
            evidenceExtractor.extractEvidence(new EvidenceExtractionRequest(a, ctx))))
        .toList();

// Wait for all
List<EvidenceExtractionResult> evidenceResults = evidenceFutures.stream()
    .map(CompletableFuture::join)
    .toList();
```

---

## Anti-Hallucination Mechanisms

1. **Evidence-only validation** - "Evaluate ONLY provided evidence, no external knowledge"
2. **Explicit missing evidence** - `missingEvidenceNote` field forces acknowledgment
3. **Skeptical defaults** - "DEFAULT to UNSUPPORTED if uncertain"
4. **Direct vs inferred distinction** - System prompts distinguish EXPLICIT/IMPLICIT/INFERRED
5. **Exact quotations required** - Validation reasoning must quote log lines

---

## System Prompts

### Assertion Generator (temp=0.3)
```
You are an expert diagnostic assertion decomposer.

Your task: Break RCA statements into 2-5 independent, testable assertions.

Rules:
- Each assertion must be independently provable/disprovable
- Focus on CLAIMS made in the RCA, not evidence analysis
- Generate targeted validation questions
- Specify what evidence would support/contradict each claim
- Extract search keywords for log scanning

Return ONLY valid JSON matching this structure:
{
  "assertions": [
    {
      "claim": "The specific claim from the RCA",
      "validationQuestion": "How to verify this claim?",
      "supportingEvidenceDescription": "What evidence would prove it",
      "contradictingEvidenceDescription": "What evidence would disprove it",
      "searchKeywords": ["keyword1", "keyword2"]
    }
  ]
}
```

### Evidence Extractor (temp=0.2)
```
You are a precise evidence citation extractor.

STRICT RULES:
- Only cite EXACT log lines/events from the provided data
- Include full timestamp and source name with each citation
- Separate supporting vs contradicting evidence
- If no evidence found, explicitly state in missingNote
- DO NOT infer or invent evidence not present in the data
- Quote the complete line, not summaries

Return ONLY valid JSON:
{
  "supportingEvidence": [
    {
      "sourceType": "POD_LOG",
      "sourceName": "pod-logs",
      "timestamp": "2026-06-18T06:09:04",
      "excerpt": "exact log line here"
    }
  ],
  "contradictingEvidence": [],
  "missingNote": "MISSING: description or null"
}
```

### Assertion Validator (temp=0.1)
```
You are a skeptical evidence validator.

CRITICAL RULES:
- Evaluate ONLY the provided evidence (no external knowledge)
- DEFAULT to UNSUPPORTED if uncertain
- Distinguish: EXPLICIT (log states it) vs IMPLICIT (log suggests) vs INFERRED (guessed)
- Quote exact log lines in your reasoning
- Confidence calibration:
    90-100: Overwhelming, unambiguous evidence
    70-89: Strong evidence, minor gaps
    50-69: Moderate evidence, some uncertainty
    30-49: Weak evidence or mixed signals
    0-29: Insufficient or contradictory evidence

Return ONLY valid JSON:
{
  "status": "SUPPORTED",
  "confidence": 95,
  "reasoning": "SUPPORTED: EXPLICIT evidence found. Exact line: '...'"
}

Status must be one of: SUPPORTED, WEAKLY_SUPPORTED, UNSUPPORTED
```

---

## Limitations

- **Requires LLM access** - Via `PromptSender` interface
- **Token costs** - 5-9 LLM calls per RCA (1 gen + N*2 extract/validate)
- **Quality depends on context** - More complete logs = better validation
- **No real-time updates** - Static validation at point in time
- **JSON parsing failures** - LLM may return malformed JSON (handled with `extractJson()`)

---

## Cost & Performance

**Typical RCA (3 assertions)**:
- **LLM Calls**: 7 (1 decompose + 3 extract + 3 validate)
- **Latency**: ~8-12 seconds with parallelization
- **Token Cost**: ~$0.02-0.05 per RCA (varies by model)

**Optimization**:
- Parallelize Phase 2 & 3 (evidence extraction and validation)
- Cache assertion decomposition for similar RCAs
- Use smaller models for evidence extraction (temp=0.2 doesn't need high reasoning)

---

## Troubleshooting

**No assertions generated**: Check RCA statement not empty, verify PromptSender connected
**Empty evidence**: Verify ValidationContext has logs/events, check search keywords relevance
**All UNSUPPORTED**: Review evidence extraction quality, check if logs contain relevant data
**JSON parse errors**: LLM returned invalid JSON - check `extractJson()` helper logs
**Low confidence scores**: Expected for partial evidence - review `missingEvidenceNote` fields

---

## Related Documentation

- **Implementation**: `src/main/java/com/causa/validation/`
- **Demo**: `src/test/java/com/causa/validation/BobValidationDemo.java`
- **Integration Guide**: `docs/validation/BOB-LLM-INTEGRATION.md`
- **Expected Output**: `docs/validation/DEMO-OUTPUT.txt`

---

## Example: Full Validation Output

```
INPUT RCA:
The root cause is unbounded memory growth due to 166,350 targets 
causing heap exhaustion with Serial GC (597ms pauses).

PHASE 1: Bob generates 3 assertions
  1. "Application crashed due to Java heap OutOfMemoryError"
  2. "166,350 targets loaded into memory"
  3. "Serial GC caused 597ms pause times"

PHASE 2 & 3: Evidence + Validation
  Assertion 1: SUPPORTED (95%) - 2 citations
    - "Aborting due to java.lang.OutOfMemoryError: Java heap space"
    
  Assertion 2: SUPPORTED (92%) - 3 citations
    - "Writer started. targets=166350"
    
  Assertion 3: WEAKLY_SUPPORTED (60%) - 1 citation, missing JFR data
    - "Java VM: ... serial gc ..." (confirms Serial GC)
    - MISSING: GC pause time metrics (would need JFR_DATA)

FINAL VERDICT: PARTIALLY_SUPPORTED (82% confidence)
  - 2 of 3 assertions fully supported
  - 1 assertion has evidence gap
```

---

## Integration with RCA Generation

```java
// After RCA generation
String generatedRca = rcaGenerator.generateRCA(context);

// Validate it
AssertionGenerationRequest req = new AssertionGenerationRequest(generatedRca);
AssertionGenerationResult result = assertionGenerator.generateAssertions(req);

// Calculate overall confidence
double avgConfidence = validateAllAssertions(result.assertions(), context);

if (avgConfidence < 70.0) {
    log.warn("RCA has low confidence ({}%), review evidence gaps", avgConfidence);
}

// Present to user with confidence score
return new RCAResult(generatedRca, avgConfidence, result);
```
