# AI Coding Session Log — Dodge AI Graph System

**Tool:** Claude (claude.ai) — Claude Sonnet  
**Platform:** https://claude.ai  
**Date:** March 25–26, 2026  
**Total Exchanges:** 80+  
**Assignment:** Forward Deployed Engineer — Graph-Based Data Modeling

---

## How I Used AI

Claude was used as the primary coding assistant throughout the entire
project — from architecture decisions to deployment troubleshooting.
The workflow was deeply collaborative: I described requirements and
errors, Claude generated solutions, I tested them, reported back what
worked or failed, and we iterated together.

This was not copy-paste development. Every error was debugged live,
every architectural decision was discussed with tradeoffs, and the
prompts evolved as the system got more complex.

---

## Key Prompts & Workflows

### Prompt 1 — Initial Architecture Decision
**What I asked:**
> "I need to build a graph-based data modeling system for SAP
> Order-to-Cash data. The dataset has orders, deliveries, invoices,
> and payments spread across 15 JSONL files. I want Spring Boot
> backend, PostgreSQL database, React frontend. Should I use Neo4j
> or PostgreSQL for the graph layer? Explain the tradeoffs for my
> specific use case."

**Why this prompt was effective:**
- Gave the full context upfront (SAP O2C, 15 JSONL files)
- Specified my tech preferences
- Asked for tradeoffs specific to MY use case, not generic advice
- Got a clear decision: PostgreSQL + JGraphT in-memory hybrid

**Outcome:** Chose PostgreSQL over Neo4j because the dataset is
fundamentally relational, SQL is better for LLM generation than
Cypher, and JGraphT handles graph traversal in-memory with zero
latency.

---

### Prompt 2 — Step by Step Backend Generation
**What I asked:**
> "Generate the complete Spring Boot project step by step. I am
> using IntelliJ. Tell me exactly what project name to use in
> Spring Initializr, which dependencies to select, and then give
> me each Java file one by one. Start with pom.xml."

**Why this prompt was effective:**
- Asked for step-by-step, not everything at once
- Specified my IDE (IntelliJ) so instructions were tailored
- Said "one by one" — got each file explained before moving on
- Mentioned Spring Initializr so I got exact UI instructions

**Outcome:** Got complete working backend in one session —
DodgeAiApplication, AppConfig, GraphNode, GraphEdge, DataLoader,
GraphService, GeminiService, GraphController, ChatController.

---

### Prompt 3 — Debugging Bean Initialization Error
**What I asked:**
> [Pasted full stack trace]
> "This error comes when I run the Spring Boot app. Both DataLoader
> and GraphService have @PostConstruct. The error says
> 'relation business_partners does not exist'. How do I fix
> the bean initialization ordering?"

**Why this prompt was effective:**
- Pasted the FULL stack trace, not a summary
- Identified what I already understood (both have @PostConstruct)
- Asked the specific question (ordering fix)

**AI Diagnosis:** GraphService was running buildGraph() before
DataLoader finished creating tables.

**Fix Applied:**
```java
// DataLoader.java
@Component("dataLoader")
public class DataLoader { ... }

// GraphService.java
@DependsOn("dataLoader")
@Service
public class GraphService { ... }
```

**Outcome:** ✅ Fixed immediately — app started correctly.

---

### Prompt 4 — Debugging PostgreSQL Column Mismatch
**What I asked:**
> [Pasted stack trace]
> "Now getting a different error after adding @DependsOn.
> PSQLException: The column name overall_delivery_status was not
> found in this ResultSet. I already have this column in my
> CREATE TABLE statement."

**Why this prompt was effective:**
- Showed the new error after the previous fix
- Highlighted the contradiction (column exists in DDL but not found)

**AI Diagnosis:** The table was created with wrong columns from a
previous broken run. Old schema was cached in PostgreSQL.

**Fix Applied:**
```sql
DROP TABLE IF EXISTS sales_order_headers CASCADE;
DROP TABLE IF EXISTS sales_order_items CASCADE;
-- ... all 14 tables
```

**Outcome:** ✅ Tables recreated correctly, all data loaded.

---

### Prompt 5 — Dataset Schema Discovery
**What I asked:**
> [Uploaded the ZIP file]
> "I have uploaded my actual dataset. Please explore it and tell
> me exactly what entities, field names, and relationships exist
> so you can generate the correct DataLoader and GraphService
> with exact column mappings."

**Why this prompt was effective:**
- Uploaded the actual data instead of describing it
- Asked for exploration first before code generation
- Requested exact column mappings (prevented future bugs)

**AI Outcome:** Explored all 15 JSONL folders, identified exact
field names like `referenceSdDocument`, `soldToParty`,
`overallOrdReltdBillgStatus`, `billingDocumentIsCancelled`.
Generated DataLoader with exact SAP field names — zero guessing.

---

### Prompt 6 — Gemini SQL Truncation Bug
**What I asked:**
> "The query 'show unpaid invoices' is failing. Here is the
> generated SQL:
> SELECT bdh.billing_document, bdh.total_net_amount, bp.full_
> FROM billing_document_headers bdh...
>
> The column name is being cut off as 'bp.full_' instead of
> 'bp.full_name'. This is happening on multiple queries.
> How do I fix the Gemini prompt to prevent truncation?"

**Why this prompt was effective:**
- Showed the exact bad SQL output
- Identified the specific pattern (column truncation)
- Asked for a prompt fix, not a code fix

**Fix Applied:** Added to GeminiService prompt:
```
CRITICAL — NEVER TRUNCATE COLUMN NAMES:
- bp.full_name (NEVER bp.full_)
- odi.reference_sd_document (NEVER odi.reference_sd_)

PROVEN SQL TEMPLATES: [15 complete SQL examples]
```

**Outcome:** ✅ SQL truncation eliminated across all queries.

---

### Prompt 7 — UI Design Matching Reference
**What I asked:**
> [Uploaded screenshot of reference UI from task PDF]
> "The UI should look exactly like this reference image. White
> background, light blue nodes, small circular dots, no labels
> on nodes, clean chat panel on right. Please rewrite all
> frontend files to match this exactly."

**Why this prompt was effective:**
- Uploaded the actual reference image
- Described specific visual elements (white bg, light blue, dots)
- Said "rewrite all files" — got complete replacement

**Outcome:** ✅ UI matches reference design exactly — white
background, physics-based graph, clean chat panel.

---

### Prompt 8 — React UI Crash Debugging
**What I asked:**
> "When I ask 'list all cancelled billing documents' the entire
> React app crashes and shows white screen. No error in chat —
> just blank page. The backend returns data correctly in Postman.
> What is causing this?"

**Why this prompt was effective:**
- Described exact query that causes crash
- Noted it works in Postman (isolates to frontend)
- Described symptom precisely (white screen, no error shown)

**AI Diagnosis:** PostgreSQL `is_cancelled` boolean column renders
as `true`/`false` in Java, which React cannot render as a JSX child
directly — causes render crash.

**Fix Applied:**
```jsx
const safeVal = (val) => {
  if (typeof val === 'boolean') return val ? 'Yes' : 'No';
  if (val === null || val === undefined) return '—';
  if (typeof val === 'object') return JSON.stringify(val).slice(0, 80);
  return String(val).slice(0, 150);
};
```

**Outcome:** ✅ All data types render safely — no more crashes.

---

### Prompt 9 — Gemini Rate Limit Fix
**What I asked:**
> [Pasted error log]
> "Getting 429 error from Gemini API. Error says quota exceeded
> for gemini-3-flash with limit of 20 per day. What model should
> I use instead and how do I add retry logic?"

**Why this prompt was effective:**
- Pasted exact error with quota details
- Asked for both model fix AND retry logic in one prompt

**Fix Applied:**
```properties
# Changed from gemini-3-flash (20/day) to:
gemini.api.url=.../gemini-1.5-flash:generateContent
# 1500 requests/day free tier
```

Added retry:
```java
if (response.statusCode() == 429) {
    Thread.sleep(2000);
    response = httpClient.send(request, ...);
}
```

**Outcome:** ✅ No more rate limit errors during development.

---

### Prompt 10 — Docker Deployment Fix
**What I asked:**
> "I deployed to Render using Docker but the API returns
> 0 nodes and 0 edges. Locally it works fine with 841 nodes.
> The database is empty on Render. What is missing?"

**Why this prompt was effective:**
- Compared local vs deployed behavior
- Gave the specific symptom (0 nodes, 0 edges)
- Mentioned Docker deployment

**AI Diagnosis:** Dockerfile was missing `COPY data ./data` —
the JSONL files were not bundled inside the Docker image.

**Fix Applied:**
```dockerfile
# Stage 1
COPY data ./data

# Stage 2
COPY --from=build /app/data ./data
```

**Outcome:** ✅ Data loads on Render — 841 nodes, 992 edges.

---

## Debugging Workflow Pattern

Every bug followed this pattern:
```
1. Run the code
2. See the error (console / UI / Postman)
3. Copy the FULL error — never summarize
4. Tell Claude: what I expected vs what happened
5. Apply the fix exactly
6. Test immediately
7. Report back — fixed or new error
8. Repeat
```

This tight feedback loop meant most bugs were fixed in
under 5 minutes.

---

## Iteration Pattern
```
Session 1 — Backend Foundation
  ├── Architecture decision (PostgreSQL vs Neo4j)
  ├── Spring Boot project setup in IntelliJ
  ├── pom.xml with exact dependencies
  ├── application.properties with env vars
  ├── All 9 Java files generated
  ├── Bug: @DependsOn fix → ✅
  └── Bug: Column mismatch → DROP tables → ✅

Session 2 — Data & Graph
  ├── Explored actual ZIP dataset
  ├── Rewrote DataLoader with exact SAP field names
  ├── Rewrote GraphService with real relationships
  └── Tested all APIs in Postman → ✅

Session 3 — LLM Integration
  ├── GeminiService with two-stage pipeline
  ├── Bug: SQL truncation → 15 templates fix → ✅
  ├── Bug: Rate limit → switched to 1.5-flash → ✅
  ├── Guardrails tested with 5+ blocked queries → ✅
  └── All 30 test queries working → ✅

Session 4 — Frontend
  ├── React + Vite setup in VS Code
  ├── GraphViewer with vis-network
  ├── ChatPanel with message bubbles
  ├── UI matched to reference design
  ├── Node click popup added
  ├── Bug: Vite vs CRA difference → ✅
  └── Bug: Boolean React crash → safeVal() → ✅

Session 5 — Deployment
  ├── GitHub repo setup
  ├── Dockerfile created
  ├── Render PostgreSQL provisioned
  ├── Backend deployed on Render
  ├── Bug: 0 nodes on deploy → COPY data fix → ✅
  ├── Bug: Gemini 401 → env vars on Render → ✅
  └── Frontend deployed on Render → ✅

Session 6 — Polish
  ├── README written with full documentation
  ├── AI session logs prepared
  ├── All test queries verified on live URL
  └── Submission ready → ✅
```

---

## Summary of AI Effectiveness

| Task | Time Without AI | Time With AI |
|---|---|---|
| Architecture decision | 2-3 hours research | 15 minutes |
| Backend (9 files) | 2-3 days | 4 hours |
| Frontend (4 files) | 1-2 days | 2 hours |
| Each bug fix | 30-60 min | 2-5 min |
| Deployment config | 2-3 hours | 30 min |
| README | 2-3 hours | 20 min |
| **Total project** | **~2 weeks** | **~2 days** |

The key insight: AI is most effective when you give it
**complete context** — full error logs, actual data files,
reference screenshots. Vague prompts give vague answers.
Specific prompts give working code.

---

## Files Generated With AI Assistance

**Backend:**
- DodgeAiApplication.java
- AppConfig.java (CORS)
- GraphNode.java
- GraphEdge.java
- DataLoader.java (15 entity loaders)
- GraphService.java (8 node types, 7 relationships)
- GeminiService.java (two-stage LLM pipeline)
- GraphController.java
- ChatController.java
- Dockerfile
- application.properties

**Frontend:**
- App.jsx
- GraphViewer.jsx (vis-network integration)
- ChatPanel.jsx (chat UI + data tables)
- client.js (API calls)
- main.jsx

**Documentation:**
- README.md
- ai-session-logs/claude-transcript.md (this file)