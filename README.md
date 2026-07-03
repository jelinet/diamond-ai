# Diamond AI

[中文](README.zh-CN.md)

Diamond AI is a local multi-agent chat workspace. It combines a Spring Boot backend (`show-engine`) with a React/Tauri frontend (`jumbotron-ui`).

## Project Structure

```text
diamond-ai/
  show-engine/      Spring Boot backend for SSE streaming orchestration
  jumbotron-ui/     React frontend with an optional Tauri desktop shell
  scripts/          Offline development scripts
  model/v1/         Local intent classifier model, tokenizer, and prototype vectors
  data/             Classifier training and evaluation data
  ARCH_STATUS.md    Current architecture status and implementation notes
```

## Core Concept

Diamond AI models different AI capabilities as three Players:

| Player | LLM | Role |
| --- | --- | --- |
| Pitcher | Claude | Structured analysis, code analysis, default Master |
| Catcher | Gemini | Challenges assumptions, finds blind spots, adds creative alternatives |
| Fielder | Codex | Engineering execution and practical implementation |

Each conversation selects one Master. The Master detects intent, coordinates the workflow, synthesizes the final answer, and also contributes directly.

## Target Architecture

```text
+--------+
|  User  |
+--------+
    |
    v
+----------+     +--------------+     +-----------+
|  Router  | --> |  Supervisor  | --> |  Planner  |
+----------+     +--------------+     +-----------+
                                            |
                                            v
      +--------------+     +------------+     +-----+
      | Search Agent |     | Code Agent |     | ... |
      +--------------+     +------------+     +-----+
                                 |
                                 v
                     +------------------+
                     | ReAct + Tool Use |
                     +------------------+
                               |
                               v
+--------------+     +---------------------+     +--------------+
| Memory + RAG | --> | Reflection / Review | --> | Final Answer |
+--------------+     +---------------------+     +--------------+
```

### Tools

```text
+----------+     +----------+     +---------+
|  Search |     | Database |     | Browser |
+----------+     +----------+     +---------+

+------+     +--------+     +-------+
| API  |     | GitHub |     | Email |
+------+     +--------+     +-------+

+----------+     +----------+     +-----+     +-----+
| Calendar |     | Terminal |     | MCP |     | ... |
+----------+     +----------+     +-----+     +-----+
```

## Current Features

- Explicit Master selection when creating a new chat.
- Hybrid intent routing:
  - rule layer
  - local ONNX classifier layer
  - LLM fallback layer
- Standalone Supervisor layer:
  - converts router results into execution plans
  - orchestrates single-agent, two-phase code, three-agent debate, and decomposed task execution flows
  - streams Supervisor / Player run state over SSE
  - provides basic failure degradation so one failed Player does not necessarily stop the whole flow
- Multi-agent task flow: decompose the task, ask the user to confirm, execute subtasks in parallel, and let the Master assemble the final answer.

## Not Implemented Yet

Main gaps:

- No general Tools abstraction yet.
- No ReAct loop yet.
- No RAG layer yet.
- Memory is currently limited to session persistence.
- Reflection is structural only; autonomous self-review is not implemented yet.
- Supervisor still lacks persisted recovery, fine-grained retry, pause/resume, and quality-review feedback loops.

## Backend: `show-engine`

### Requirements

- Java 21
- Maven
- Player CLI tools configured:
  - Pitcher: Claude CLI
  - Catcher: Antigravity/Gemini CLI
  - Fielder: Codex CLI

### Run

```bash
cd show-engine
mvn spring-boot:run
```

The default port is `8080`.

### Build

```bash
cd show-engine
mvn -q -DskipTests package
```

## Frontend: `jumbotron-ui`

### Requirements

- Node.js
- npm
- Rust toolchain if running the Tauri desktop shell

### Browser Development

```bash
cd jumbotron-ui
npm install
npm run dev
```

### Build

```bash
cd jumbotron-ui
npm run build
```

### Test

```bash
cd jumbotron-ui
npm run test
```

### Tauri Development

```bash
cd jumbotron-ui
npm run tauri dev
```

## Local Intent Model

The classifier reads `model/v1/` by default:

```text
model/v1/
  encoder.onnx
  prototypes.json
  tokenizer/
```

Override the model directory with:

```bash
SHOW_ENGINE_MODEL_DIR=/path/to/model/v1
```

If the local model fails to initialize, the backend skips the model layer and falls back to the LLM intent router.

Training and evaluation scripts live in:

```text
scripts/intent-classifier/
```

See [scripts/intent-classifier/README.md](scripts/intent-classifier/README.md) for usage.
