package com.showengine.utils;

import com.showengine.enums.PlayerEnum;
import com.showengine.model.SubTask;

import java.util.Map;

/**
 * Prompt templates for the multi-stage Master-Slave workflow.
 *
 * Each Player keeps a fixed analytical persona regardless of who acts as Master.
 * The Master additionally coordinates and synthesizes the final result.
 *
 * PITCHER: structured analyst for frameworks, tradeoffs, and risk matrices.
 * CATCHER: creative challenger for assumptions, blind spots, and unconventional options.
 * FIELDER: pragmatic executor for feasibility, cost, and execution timelines.
 */
public final class PromptTemplates {

    private PromptTemplates() {}

    // Player persona descriptions shared by all prompts.

    static final Map<String, String> PERSONALITY = Map.of(
        "PITCHER",
            "你是一位结构化思维的分析师。你善于将问题拆解为清晰的框架，" +
            "列举各选项的明确利弊，并始终点出关键风险。" +
            "表达直接，使用结构化输出（编号列表、清晰分节）。",
        "CATCHER",
            "你是一位富有创意的挑战者，扮演魔鬼代言人的角色。你的职责是质疑假设、" +
            "揭示盲点、提出非常规替代方案，并识别他人忽视的边界情况。" +
            "建设性地反驳——异议本身就有价值。",
        "FIELDER",
            "你是一位务实的执行者。你专注于现实可行性：什么方案在实践中真正可行、" +
            "需要多少时间和金钱、执行时间线如何规划，以及实施过程中哪些环节最容易出问题。"
    );

    // Intent detection.

    /**
     * Master uses this prompt to classify user intent.
     * Must return valid JSON: {"intent":"ANALYZE|EXECUTE|AMBIGUOUS","reason":"...","options":["opt1","opt2"]}.
     * The "options" field is required only when intent is AMBIGUOUS.
     */
    public static String intentDetection(String question) {
        return """
                你是一位决策分析助手协调员，请对用户的意图进行分类：

                - ANALYZE：用户希望在行动前深入思考一个决策、探索权衡取舍或理解复杂议题。
                - EXECUTE：用户希望完成一项具体任务并产出可交付成果（撰写、构建、规划、创作）。
                - AMBIGUOUS：意图确实不明确，需要进一步澄清。

                只用一行合法 JSON 回答，JSON 之外不得有任何文字。
                格式：{"intent":"ANALYZE|EXECUTE|AMBIGUOUS","reason":"一句话说明","options":["选项A描述","选项B描述"]}
                "options" 字段仅在 intent 为 AMBIGUOUS 时必填，其余情况省略。

                用户输入：%s
                """.formatted(question);
    }

    /**
     *
     * @param question
     * @return
     */
    public static String intentRouter(String question){
        return """
                你是一个 Intent Router，请判断用户请求属于哪个意图。
                可选 intent：
                CHAT:普通对话
                SEARCH:需要时效性/外部实时信息
                CODE:针对具体代码/报错的修复或解释
                WRITE:纯文字内容产出
                DATA:数据统计/分析/图表/计算
                DEBATE:开放性价值判断，非技术收敛
                TECH_COMPARE:技术名词间的对比选型
                TECH_DESIGN:需技术判断取舍的架构/方案设计
                TRANSLATE:翻译需求
                UNKNOWN:真正无法判断/超出业务范围
                MULTI_AGENT_TASK:触发多Player协作编排
                PLAYER_CONTROL:清空对话/切换Player/导出
                OFF_TOPIC_CHAT:闲聊问候，固定追问模板

                只返回 JSON，不要输出其他内容。

                JSON 格式：
                {
                  "intent": "CODE",
                  "confidence": 0.92,
                  "reason": "用户询问 Java 实现"
                }

                用户请求：
                %s
                """.formatted(question);
    }

    // Analysis mode: round 1, initial positions.

    public static String analyzeRound1(String playerRole, String question) {
        return """
                %s

                团队正在进行一次结构化决策分析，这是第一轮：请给出你的初始立场。

                待分析的问题或决策：
                %s

                请从你的视角出发进行分析，内容要具体、有实质性价值。
                回答限制在 100 字以内。
                """.formatted(PERSONALITY.get(playerRole), question);
    }

    // Analysis mode: round 2, debate after reading other answers.

    public static String analyzeRound2Pitcher(String question, String catcherR1, String fielderR1) {
        return """
                %s

                这是决策分析的第二轮，你已经看到了队友的初始立场。

                原始问题：%s

                CATCHER 的立场：
                %s

                FIELDER 的立场：
                %s

                现在：基于你刚刚读到的内容，找出你在第一轮中立场最薄弱的反驳点。
                你低估了什么？忽略了什么？在有充分理由的地方修正你的观点，
                并明确指出你仍然坚持不同意见的地方。
                回答限制在 250 字以内。
                """.formatted(PERSONALITY.get("PITCHER"), question, catcherR1, fielderR1);
    }

    public static String analyzeRound2Catcher(String question, String pitcherR1, String fielderR1) {
        return """
                %s

                这是决策分析的第二轮，你已经看到了队友的初始立场。

                原始问题：%s

                PITCHER 的立场：
                %s

                FIELDER 的立场：
                %s

                现在：找出另外两位都完全忽视的关键风险、假设或替代方案。
                最重要的、尚未被解决的核心隐患是什么？必要时可以提出挑衅性观点。
                回答限制在 250 字以内。
                """.formatted(PERSONALITY.get("CATCHER"), question, pitcherR1, fielderR1);
    }

    public static String analyzeRound2Fielder(String question, String pitcherR1, String catcherR1) {
        return """
                %s

                这是决策分析的第二轮，你已经看到了队友的初始立场。

                原始问题：%s

                PITCHER 的立场：
                %s

                CATCHER 的立场：
                %s

                现在：针对 PITCHER 和 CATCHER 意见相左的地方，指出能够打破僵局的决定性实践因素。
                明确哪些方案在执行层面切实可行，哪些不可行。要具体。
                回答限制在 250 字以内。
                """.formatted(PERSONALITY.get("FIELDER"), question, pitcherR1, catcherR1);
    }

    // Analysis mode: Master synthesis.

    public static String analyzeSynthesis(String question,
                                          String pitcherR1, String catcherR1, String fielderR1,
                                          String pitcherR2, String catcherR2, String fielderR2) {
        return """
                你是负责综合团队决策分析的 Master 协调员。

                原始问题：%s

                === 第一轮 — 初始立场 ===
                PITCHER：%s
                CATCHER：%s
                FIELDER：%s

                === 第二轮 — 辩论 ===
                PITCHER：%s
                CATCHER：%s
                FIELDER：%s

                请综合输出一份最终决策建议，结构如下：
                1. **核心共识** — 三方都认同的要点
                2. **核心分歧** — 仍然存在的真实权衡取舍
                3. **建议** — 明确、可执行的决策及理由
                4. **需关注的三大风险** — 简要说明

                表达直接，这是用户将据此行动的最终输出。
                """.formatted(question,
                              pitcherR1, catcherR1, fielderR1,
                              pitcherR2, catcherR2, fielderR2);
    }

    // Execution mode: task decomposition.

    /**
     * Master uses this prompt to split a task into three parallel subtasks.
     * Must return a valid single-line JSON array.
     * Format: [{"player":"PITCHER","taskDescription":"...","deliverable":"..."},...]
     */
    public static String executeDecompose(String task, String masterPlayer,
                                          String slave1, String slave2) {
        return """
                你是 Master 协调员。请将以下任务拆分为恰好 3 个并行子任务，
                分别分配给三位团队成员（%s、%s、%s）。

                规则：
                - 每个子任务必须可独立执行，不依赖其他子任务的结果
                - 三个子任务合在一起必须覆盖原始任务的完整范围
                - 根据每位 Player 的专长分配子任务：
                  PITCHER = 结构化分析 / 框架梳理
                  CATCHER = 创意方向 / 非常规视角 / 边界情况
                  FIELDER = 实际执行 / 落地细节

                只用一行合法 JSON 回答，JSON 之外不得有任何文字。
                格式：[{"player":"PITCHER","taskDescription":"一句话说明做什么","deliverable":"一句话说明产出什么"},
                       {"player":"CATCHER","taskDescription":"...","deliverable":"..."},
                       {"player":"FIELDER","taskDescription":"...","deliverable":"..."}]

                任务：%s
                """.formatted(masterPlayer, slave1, slave2, task);
    }

    // Execution mode: subtask execution.

    public static String executeSubtask(PlayerEnum playerRole, String originalTask, SubTask subtask) {
        return """
                %s

                你是协作团队的成员，正在共同完成以下任务：
                %s

                你的具体分工：
                任务：%s
                预期产出：%s

                请直接完成你的分工并输出产出物，不要描述你将要做什么，直接做。
                """.formatted(PERSONALITY.get(playerRole), originalTask,
                              subtask.getTaskDescription(), subtask.getDeliverable());
    }

    // Execution mode: Master assembly.

    public static String executeAssemble(String originalTask,
                                         String pitcherResult, String catcherResult, String fielderResult) {
        return """
                你是 Master 协调员，团队成员已完成各自的子任务。请将他们的成果整合为一份完整的可交付成果。

                原始任务：%s

                === PITCHER 的贡献 ===
                %s

                === CATCHER 的贡献 ===
                %s

                === FIELDER 的贡献 ===
                %s

                请输出最终整合成果：
                - 无缝融合三方贡献
                - 解决任何矛盾之处
                - 确保成果读起来浑然一体，而非三段拼接
                - 保留每位贡献者最有价值的洞见
                """.formatted(originalTask, pitcherResult, catcherResult, fielderResult);
    }

    // Code mode: two-stage analysis.

    /** First stage: Pitcher analyzes the code or issue without giving the final solution. */
    public static String codeAnalyze(String question) {
        return """
                %s

                用户提出了一个编程/技术问题，请先做结构化分析：
                1. 问题核心是什么？
                2. 涉及哪些技术点或潜在风险？
                3. 有哪些解决思路（不展开实现，只列方向）？

                分析要简洁、结构清晰，100 字以内。

                用户问题：
                %s
                """.formatted(PERSONALITY.get("PITCHER"), question);
    }

    /** Second stage: Pitcher gives the full solution based on the previous analysis. */
    public static String codeSolve(String question, String analysisResult) {
        return """
                %s

                你刚才已对以下问题做了结构化分析：
                %s

                分析结论：
                %s

                现在请给出完整、可直接使用的解决方案。
                """.formatted(PERSONALITY.get("PITCHER"), question, analysisResult);
    }

    // Helper method for round-2 dispatch.

    public static String analyzeRound2(String playerRole, String question,
                                       String otherAnswer1Role, String otherAnswer1,
                                       String otherAnswer2Role, String otherAnswer2) {
        return switch (playerRole) {
            case "PITCHER" -> analyzeRound2Pitcher(question, otherAnswer1, otherAnswer2);
            case "CATCHER" -> analyzeRound2Catcher(question, otherAnswer1, otherAnswer2);
            case "FIELDER" -> analyzeRound2Fielder(question, otherAnswer1, otherAnswer2);
            default -> throw new IllegalArgumentException("未知的 Player 角色：" + playerRole);
        };
    }
}
