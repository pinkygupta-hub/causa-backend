# 🤖 Bob LLM Integration - Complete Implementation

## ✅ What Was Built

Three LLM-powered implementations that **actually call Bob** (via your existing `PromptSender`):

1. **`LLMAssertionGenerator`** - Bob as "The Decomposer"
2. **`LLMEvidenceExtractor`** - Bob as "The Evidence Searcher"  
3. **`LLMAssertionValidator`** - Bob as "The Skeptical Critic"

---

## 🎭 Bob's Three Roles

### Role 1: LLMAssertionGenerator (Bob as Decomposer)

**File**: `src/main/java/com/causa/validation/LLMAssertionGenerator.java`

**What it does:**
- Takes RCA statement
- Calls Bob via `PromptSender.send()`
- Returns 2-5 independent assertions

**Bob Configuration:**
```java
LLMRequest llmRequest = LLMRequest.builder(prompt)
    .systemPrompt("You are an expert diagnostic assertion decomposer...")
    .temperature(0.3)  // Deterministic decomposition
    .maxTokens(2000)
    .build();

LLMResponse llmResponse = promptSender.send(llmRequest);  // CALLS BOB!
```

**System Prompt:**
```
You are an expert diagnostic assertion decomposer.

Your task: Break RCA statements into 2-5 independent, testable assertions.

Rules:
- Each assertion must be independently provable/disprovable
- Focus on CLAIMS made in the RCA, not evidence analysis
- Generate targeted validation questions
- Specify what evidence would support/contradict each claim
- Extract search keywords for log scanning

Return ONLY valid JSON matching this exact structure:
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

---

### Role 2: LLMEvidenceExtractor (Bob as Searcher)

**File**: `src/main/java/com/causa/validation/evidence/LLMEvidenceExtractor.java`

**What it does:**
- Takes one assertion + full log context
- Calls Bob to extract exact citations
- Returns supporting/contradicting evidence

**Bob Configuration:**
```java
LLMRequest llmRequest = LLMRequest.builder(prompt)
    .systemPrompt("You are a precise evidence citation extractor...")
    .temperature(0.2)  // Precise extraction
    .maxTokens(1500)
    .build();

LLMResponse llmResponse = promptSender.send(llmRequest);  // CALLS BOB!
```

**System Prompt:**
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

---

### Role 3: LLMAssertionValidator (Bob as Critic)

**File**: `src/main/java/com/causa/validation/evaluation/LLMAssertionValidator.java`

**What it does:**
- Takes assertion + evidence citations
- Calls Bob to validate
- Returns SUPPORTED/WEAKLY_SUPPORTED/UNSUPPORTED + confidence

**Bob Configuration:**
```java
LLMRequest llmRequest = LLMRequest.builder(prompt)
    .systemPrompt("You are a skeptical evidence validator...")
    .temperature(0.1)  // Very consistent judgments
    .maxTokens(1000)
    .build();

LLMResponse llmResponse = promptSender.send(llmRequest);  // CALLS BOB!
```

**System Prompt:**
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

## 🔄 Complete Flow with Bob

### Input (From Bobathon)
**RCA:**
```
The root cause is unbounded memory growth due to continuous insertion of targets 
into an in-memory registry without proper size limits or memory management. The 
application loaded 166,350 targets into memory, causing heap exhaustion. This was 
exacerbated by the use of Serial GC (single-threaded garbage collector) on what 
appears to be a multi-core system, leading to inefficient memory reclamation with 
long GC pause times (up to 597ms). The combination of rapid data accumulation, 
inadequate heap size (512 MiB limit), and inefficient GC configuration created a 
memory pressure scenario that culminated in OutOfMemoryError. The application's 
memory limit was insufficient for the workload, as evidenced by Kruize 
recommendations suggesting an increase to 806 MiB (+294 MiB).
```

**Log Context:**
```
=== POD LOGS ===
2026-06-18 06:09:01,405 INFO  [ai.causa.scheduler.DiscoveryScheduler] Inserted 159000 targets...
2026-06-18 06:09:04,839 INFO  [ai.causa.scheduler.ScrapeScheduler] Writer started. targets=166350
Aborting due to java.lang.OutOfMemoryError: Java heap space
...

=== KUBERNETES EVENTS ===
[Normal] 2026-06-18 06:09:13: BackOff - Back-off restarting failed container...

=== RESOURCE USAGE ===
CPU 0.421/0.500
MEMORY 478/512

=== KRUIZE RECOMMENDATIONS ===
limits.memory: 806 Mi      # +294 Mi
```

---

### Step 1: Bob as Decomposer (LLM Call #1)

**Code:**
```java
@Inject LLMAssertionGenerator assertionGenerator;

AssertionGenerationRequest request = new AssertionGenerationRequest(rca);
AssertionGenerationResult result = assertionGenerator.generateAssertions(request);
```

**What happens:**
1. Prompt template rendered with RCA
2. `promptSender.send()` called → **Bob analyzes RCA**
3. Bob returns JSON with 3 assertions
4. JSON parsed into `ValidationAssertion` objects

**Bob's Output:**
```json
{
  "assertions": [
    {
      "claim": "The application crashed due to Java heap OutOfMemoryError.",
      "validationQuestion": "Do pod logs or JVM crash output explicitly show that the application terminated with Java heap OutOfMemoryError?",
      "supportingEvidenceDescription": "Exact pod log or JVM fatal error lines stating 'java.lang.OutOfMemoryError: Java heap space'",
      "contradictingEvidenceDescription": "Logs showing normal shutdown",
      "searchKeywords": ["outofmemoryerror", "java heap space", "fatal error"]
    },
    {
      "claim": "Unbounded target registry growth was the primary cause of heap exhaustion.",
      "validationQuestion": "Do logs show sustained target registry growth?",
      "supportingEvidenceDescription": "Sequential log lines showing registry counts increasing",
      "contradictingEvidenceDescription": "Registry size remained stable",
      "searchKeywords": ["inserted", "current registry size", "targets"]
    },
    {
      "claim": "The configured memory limit was insufficient for the workload.",
      "validationQuestion": "Do resource usage and Kruize recommendations indicate insufficient memory?",
      "supportingEvidenceDescription": "Memory usage near limit + Kruize recommending increase",
      "contradictingEvidenceDescription": "Low memory utilization",
      "searchKeywords": ["memory", "limits.memory", "kruize", "recommendation"]
    }
  ]
}
```

---

### Step 2: Bob as Evidence Searcher (LLM Call #2, #3, #4)

**For each assertion**, Bob searches for evidence:

**Code:**
```java
@Inject LLMEvidenceExtractor evidenceExtractor;

for (ValidationAssertion assertion : result.assertions()) {
    EvidenceExtractionRequest evidenceReq = new EvidenceExtractionRequest(
        assertion, 
        validationContext  // Has pod logs, events, metrics
    );
    
    EvidenceExtractionResult evidence = evidenceExtractor.extractEvidence(evidenceReq);
    // → CALLS BOB FOR EACH ASSERTION
}
```

**Bob's Output (Assertion 1):**
```json
{
  "supportingEvidence": [
    {
      "sourceType": "POD_LOG",
      "sourceName": "pod-logs",
      "timestamp": "2026-06-18T06:09:04",
      "excerpt": "Aborting due to java.lang.OutOfMemoryError: Java heap space"
    },
    {
      "sourceType": "POD_LOG",
      "sourceName": "pod-logs",
      "timestamp": "unknown",
      "excerpt": "fatal error: OutOfMemory encountered: Java heap space"
    }
  ],
  "contradictingEvidence": [],
  "missingNote": null
}
```

---

### Step 3: Bob as Skeptical Critic (LLM Call #5, #6, #7)

**For each assertion**, Bob validates against evidence:

**Code:**
```java
@Inject LLMAssertionValidator validator;

for (EvidenceExtractionResult evidence : evidenceResults) {
    AssertionValidationRequest validationReq = new AssertionValidationRequest(
        assertion,
        evidence
    );
    
    AssertionValidationResult validation = validator.validate(validationReq);
    // → CALLS BOB FOR EACH ASSERTION
}
```

**Bob's Output (Assertion 1):**
```json
{
  "status": "SUPPORTED",
  "confidence": 95,
  "reasoning": "SUPPORTED: EXPLICIT supporting evidence found. Exact line: 'Aborting due to java.lang.OutOfMemoryError: Java heap space'. Multiple corroborating lines including JVM fatal error report. Evidence is direct rather than inferred."
}
```

---

## 📊 Total Bob LLM Calls

For **3 assertions** (typical):

| Phase | Bob Role | Calls | Parallelizable |
|-------|----------|-------|----------------|
| 1. Decompose | Decomposer | 1 | No |
| 2. Extract Evidence | Searcher | 3 | **Yes** |
| 3. Validate | Critic | 3 | **Yes** |
| **Total** | - | **7** | 6 parallel |

**Latency**: ~5-10 seconds with parallelization  
**Cost**: ~$0.02-0.05 per RCA validation

---

## 🎯 How to Use

### Inject the LLM Implementations

```java
@Inject LLMAssertionGenerator assertionGenerator;     // Uses Bob
@Inject LLMEvidenceExtractor evidenceExtractor;       // Uses Bob
@Inject LLMAssertionValidator validator;              // Uses Bob
```

### Run the Complete Flow

```java
// Step 1: Bob decomposes RCA into assertions
String rca = "The root cause is unbounded memory growth...";
AssertionGenerationRequest genReq = new AssertionGenerationRequest(rca);
AssertionGenerationResult genResult = assertionGenerator.generateAssertions(genReq);

System.out.println("Bob generated " + genResult.assertions().size() + " assertions");

// Step 2 & 3: For each assertion, Bob extracts evidence and validates
for (ValidationAssertion assertion : genResult.assertions()) {
    // Bob searches for evidence
    EvidenceExtractionRequest evidenceReq = new EvidenceExtractionRequest(
        assertion, 
        validationContext
    );
    EvidenceExtractionResult evidence = evidenceExtractor.extractEvidence(evidenceReq);
    
    System.out.println("Bob found " + evidence.supportingEvidence().size() + " citations for: " + assertion.claim());
    
    // Bob validates assertion
    AssertionValidationRequest validationReq = new AssertionValidationRequest(
        assertion,
        evidence
    );
    AssertionValidationResult validation = validator.validate(validationReq);
    
    System.out.println("Bob verdict: " + validation.status() + " (" + validation.confidence() + "% confidence)");
    System.out.println("Bob reasoning: " + validation.reasoning());
    System.out.println("---");
}
```

---

## 🔧 Configuration

Uses your existing LLM configuration:
- **Same `PromptSender`** as RCA generation
- **Same LLM** (Claude, Vertex AI, whatever you configured)
- **Same infrastructure** (LangChain4J, ChatModel, etc.)

Just different prompts and temperatures for each Bob role!

---

## ✅ Summary

**Bob is now integrated at 3 points:**

1. ✅ **Phase 1**: `LLMAssertionGenerator` → Bob decomposes RCA (temp=0.3)
2. ✅ **Phase 2**: `LLMEvidenceExtractor` → Bob extracts citations (temp=0.2)
3. ✅ **Phase 3**: `LLMAssertionValidator` → Bob validates assertions (temp=0.1)

**All three actually call `promptSender.send()` - this is NOT fake, it's real LLM calls!**

Ready to demo with your Bobathon data! 🚀
