# ResumeAgent

A command line based AI agent that tailors a resume to a specific job description using an LLM-driven pipeline, then renders it to `.docx` and `.pdf`.

Instead of one big "rewrite my resume" prompt, the JD and resume are pushed through five focused, single-purpose LLM calls — each with a narrow job and strict rules against inventing skills, metrics, or claims not already present in the source resume.

## How it talks to the LLM

Every call goes through one interface, `LlmClient.call(systemPrompt, userPrompt)`, so the whole pipeline is provider-agnostic — swapping models is a one-line config change, no other code touches HTTP.

```
                              AppConfig.PROVIDER
                                     │
                                     ▼
                            ┌─────────────────┐
                            │ LlmClientFactory │
                            └────────┬────────┘
                                     │ creates
                                     ▼
                            ┌─────────────────┐
                            │   LlmClient      │  interface: call(system, user) -> String
                            └────────┬────────┘
              ┌───────────┬──────────┼──────────┬───────────┐
              ▼           ▼          ▼          ▼           ▼
        ClaudeClient  OpenRouter  GeminiClient  OllamaClient NvidiaClient
        (Anthropic)   Client                    (local)     (NIM)
```

### The pipeline (`ResumeAgent.tailor`)

```
 jd.txt ──┐
          │                                            resume.txt ──┐
          ▼                                                          ▼
   ┌─────────────────────┐                                  ┌─────────────────┐
   │ RequirementExtractor │◀── LLM #1: extract-requirements ─┤   ResumeParser   │
   └──────────┬───────────┘    JD text → [{description,      └────────┬────────┘
              │                priority: HIGH/MED/LOW}]                │
              ▼                                                        │
     List<JobRequirement>                                              │
              │                                                        │
   ┌──────────┴─────────────────────────────────────────┐              │
   ▼                                                     ▼              │
┌────────────────┐                              ┌─────────────────────┐│
│  SummaryTailor  │◀ LLM #2: tailor-summary       │ CompetencyReorderer ││◀ LLM: reorder-competencies
└────────┬────────┘  rewrite summary, weave in    └──────────┬──────────┘  reorder skill categories
         │           HIGH-priority terms                     │           & skills within them, no add/remove
         │                                                    │
         ▼                                                    ▼
  tailored summary                                   ordered competencies
                                                                          │
   For each EmploymentEntry ◀──────────────────────────────────────────┘
              │
              ▼
   ┌─────────────────────────────────────────────────────────────┐
   │                    BulletSelectionLoop                      │
   │                                                               │
   │  LLM #3  score-bullets     every bullet scored 0-10 vs reqs  │
   │             │                                                │
   │             ▼                                                │
   │  threshold-filter + sort + cap (MAX_BULLETS)                 │
   │             │                                                │
   │             ▼                                                │
   │  ┌────────────────────────────────────────────┐  loop up to  │
   │  │ LLM #4  check-coverage                      │  MAX_LOOP_  │
   │  │   "do selected bullets cover HIGH reqs?"    │  ITER times │
   │  │   -> {acceptable, coverage_score, uncovered}│              │
   │  └───────────────┬──────────────────────────────┘              │
   │            acceptable?──yes──▶ done                            │
   │                  │no                                          │
   │                  ▼                                            │
   │  pick next best-scoring unselected bullet                     │
   │                  │                                            │
   │                  ▼                                            │
   │  LLM #5  modify-bullet   minimally edit bullet (≤2 words)      │
   │            to address the uncovered requirement                │
   │                  │                                            │
   │                  └──────────────▶ add to selection, re-check  │
   └─────────────────────────────────────────────────────────────┘
              │
              ▼
        TailoredResume (summary, competencies, entries[])
              │
              ▼
   StateSerializer → JSON "state" document (lines/sections model)
              │
              ▼
   node render/render.js state.json out.docx style   (docx templating)
              │
              ▼
   PdfConverter → soffice --headless --convert-to pdf   (LibreOffice)
              │
              ▼
   ~/clauderesume/{Company}/purshotam_yadav.{docx,pdf}
```

### LLM calls at a glance

| # | Class | Purpose | Guardrail |
|---|-------|---------|-----------|
| 1 | `RequirementExtractor` | Extract HIGH/MED/LOW requirements from the JD | Only requirements explicitly stated in the JD text — no inference |
| 2 | `SummaryTailor` | Rewrite the professional summary | Max 3 lines, no buzzwords, no invented achievements |
| — | `CompetencyReorderer` | Reorder skill categories & skills within them | Must not add or remove any skill/category |
| 3 | `BulletSelectionLoop.scoreBullets` | Score every bullet 0–10 against requirements | — |
| 4 | `BulletSelectionLoop.checkCoverage` | Verify ≥70% of HIGH requirements are covered by selection | — |
| 5 | `BulletSelectionLoop.modifyBullet` | Minimally patch a bullet to close a coverage gap | Substitute at most 2 words, no new metrics/claims |

## Supported LLM providers

Set via `AppConfig.PROVIDER` (`-Dllm.provider=...`), default is `NVIDIA`:

| Provider | Client | Notes |
|---|---|---|
| `ANTHROPIC` | `ClaudeClient` | requires `ANTHROPIC_API_KEY` |
| `OPENROUTER` | `OpenRouterClient` | requires `OPENROUTER_API_KEY` |
| `GEMINI` | `GeminiClient` | requires `GEMINI_API_KEY` |
| `NVIDIA` | `NvidiaClient` | requires `NVIDIA_API_KEY` (NIM models, e.g. DeepSeek) |
| `OLLAMA` | `OllamaClient` | local, no key — must have the model pulled |

## Project structure

```
src/main/java/org/example/
├── Main.java                 entry point: reads JD, wires everything, writes output
├── config/AppConfig.java     provider selection, model IDs, thresholds, paths
├── llm/                       LlmClient interface + 5 provider implementations, JsonUtil
├── agent/
│   ├── ResumeAgent.java       orchestrates the pipeline stages
│   ├── RequirementExtractor.java
│   ├── SummaryTailor.java
│   ├── CompetencyReorderer.java
│   ├── BulletSelectionLoop.java
│   └── PromptTemplates.java   every prompt sent to the LLM
├── parser/ResumeParser.java   plaintext resume.txt -> Resume model
├── model/                     Resume, Contact, EmploymentEntry, CompetencyCategory,
│                               JobRequirement, TailoredEntry, TailoredResume
└── writer/
    ├── StateSerializer.java   Resume/TailoredResume -> JSON state, invokes render.js
    └── PdfConverter.java      docx -> pdf via LibreOffice (soffice --headless)
render/
├── render.js                  Node docx templating engine (uses `docx` npm package)
└── package.json
```

## Prerequisites

- JDK 24 and Maven
- Node.js with `render/node_modules` installed (`cd render && npm install`)
- LibreOffice (`soffice`) on PATH for PDF conversion — optional, DOCX is still produced without it
- An API key for whichever provider you select, exported as an env var (e.g. `NVIDIA_API_KEY`)

## Usage

```bash
./run.sh [jd.txt] [output.docx]
```

This builds the project with Maven and runs `org.example.Main`. The base resume is read from the bundled classpath resource `resume.txt`; the JD is read from the given path (default `jd.txt`). Output is also written to `~/clauderesume/{Company}/purshotam_yadav.{docx,pdf}`, where `{Company}` is derived from the first non-blank line of the JD file.

To override the LLM provider or model at runtime:

```bash
JAVA_HOME=$JAVA_HOME "$JAVA_HOME/bin/java" -Dllm.provider=ANTHROPIC \
  -cp "target/ResumeAgent-1.0-SNAPSHOT.jar:target/dependency/*" org.example.Main jd.txt
```

## Configuration

Key knobs live in `AppConfig`:

- `MAX_BULLETS` (10) — cap on bullets selected per employer
- `MAX_LOOP_ITER` (3) — max coverage-check/modify iterations per employer
- `SCORE_THRESHOLD` (6) — minimum bullet relevance score to be auto-selected
- `STYLE` (`classic`) — passed through to `render.js` as the docx style
