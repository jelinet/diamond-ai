# Diamond AI

[English](README.md)

Diamond AI 是一个本地多 Agent 对话工作台。它由 Spring Boot 后端 `show-engine` 和 React/Tauri 前端 `jumbotron-ui` 组成;

## 项目结构

```text
diamond-ai/
  show-engine/      Spring Boot 后端，负责 SSE 流式编排
  jumbotron-ui/     React 前端，可选 Tauri 桌面壳
  scripts/          离线开发脚本
  model/v1/         本地意图分类模型、tokenizer 和原型向量
  data/             分类器训练/评估数据
  ARCH_STATUS.md    当前架构状态和实现说明
```

## 核心概念

Diamond AI 把不同 AI 能力抽象成三个 Player：

| Player | LLM | 职责 |
| --- | --- | --- |
| Pitcher | Claude | 结构化分析、代码分析、默认 Master |
| Catcher | Gemini | 挑战假设、发现盲点、补充创意方案 |
| Fielder | Codex | 工程执行、落地实现 |

每个会话会选择一个 Master。Master 负责意图判断、流程协调和最终综合，同时也会参与实际回答。

## 目标架构图

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

## 当前功能

- 新建对话时显式选择 Master。
- 混合意图路由：
  - 规则层
  - 本地 ONNX 分类器层
  - LLM 兜底层
- 多 Agent 任务支持先分解任务，用户确认后并行执行，再由 Master 汇总。

## 尚未实现

当前主要缺口：

- 还没有独立 Supervisor 层。
- 还没有通用 Tools 抽象。
- 还没有 ReAct 循环。
- 还没有 RAG 层。
- Memory 目前主要是会话持久化。
- Reflection 只有结构雏形，还没有自主反思能力。

## 后端：`show-engine`

### 环境要求

- Java 21
- Maven
- 已配置对应 Player 的 CLI：
  - Pitcher：Claude CLI
  - Catcher：Antigravity/Gemini CLI
  - Fielder：Codex CLI

### 启动

```bash
cd show-engine
mvn spring-boot:run
```

默认端口是 `8080`。

### 打包

```bash
cd show-engine
mvn -q -DskipTests package
```

## 前端：`jumbotron-ui`

### 环境要求

- Node.js
- npm
- 如果运行 Tauri 桌面壳，还需要 Rust 工具链

### 浏览器开发模式

```bash
cd jumbotron-ui
npm install
npm run dev
```

### 构建

```bash
cd jumbotron-ui
npm run build
```

### 测试

```bash
cd jumbotron-ui
npm run test
```

### Tauri 开发模式

```bash
cd jumbotron-ui
npm run tauri dev
```

## 本地意图模型

分类器默认读取 `model/v1/`：

```text
model/v1/
  encoder.onnx
  prototypes.json
  tokenizer/
```

可以用环境变量覆盖模型目录：

```bash
SHOW_ENGINE_MODEL_DIR=/path/to/model/v1
```

如果本地模型初始化失败，后端会跳过模型层，直接降级到 LLM 意图路由。

训练和评估脚本位于：

```text
scripts/intent-classifier/
```

使用方式见 [scripts/intent-classifier/README.md](scripts/intent-classifier/README.md)。
