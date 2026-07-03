package com.showengine.router.service.impl;

import com.showengine.router.enums.IntentSource;
import com.showengine.router.enums.IntentTypeEnum;
import com.showengine.model.AskRequest;
import com.showengine.router.model.IntentResult;
import com.showengine.router.service.IntentRouterService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class RuleBasedIntentRouterImpl implements IntentRouterService {

    // Rule-layer intercepts with the highest priority.

    /** Switch Player: @PITCHER / @CATCHER / @FIELDER. */
    private static final Pattern SWITCH_PLAYER_PATTERN = Pattern.compile(
            ".*@(PITCHER|CATCHER|FIELDER|投手|捕手|野手).*",
            Pattern.CASE_INSENSITIVE
    );

    /** Clear the conversation. */
    private static final Pattern CLEAR_PATTERN = Pattern.compile(
            ".*/clear|清空(对话|记忆|历史)|重新开始|开始新对话.*",
            Pattern.CASE_INSENSITIVE
    );

    /** Export the conversation. */
    private static final Pattern EXPORT_PATTERN = Pattern.compile(
            ".*/export|导出(对话|记录)|保存对话.*",
            Pattern.CASE_INSENSITIVE
    );

    /** Multi-Player collaborative task, previously TASK_PLAN. */
    private static final Pattern MULTI_AGENT_PATTERN = Pattern.compile(
            ".*(帮我(做|完成|执行)|多步骤|并行(执行|处理)|分工合作|协作完成|一起帮我).*",
            Pattern.CASE_INSENSITIVE
    );

    /** Casual greeting or small talk. */
    private static final Pattern OFF_TOPIC_PATTERN = Pattern.compile(
            "^([你您]好[啊呀！!]?|哈喽|嗨|hi|hello|早上好|晚上好|晚安|在吗|在不在|无聊|闲聊|陪我聊聊)$",
            Pattern.CASE_INSENSITIVE
    );

    // Rules inside the three-stage funnel.

    private static final Pattern CODE_PATTERN = Pattern.compile(
            ".*(java|python|redis|spring|sql|代码|报错|bug|接口|函数|类|线程|并发|数据库|linux|docker).*",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern CHAT_PATTERN = Pattern.compile(
            ".*(你开心吗|你高兴吗|你难过吗|你怎么样|今天.*(开心|高兴|难过|不开心)|我的(猫|狗).*(开心|高兴|难过|不开心)).*",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern SEARCH_PATTERN = Pattern.compile(
            ".*(最新|实时|查一下|查下|搜索|官网|新闻|价格|版本|政策|招聘|动态|汇率|股市|行情|天气|比赛结果|营业|附近|高铁票|演唱会|菜单|QPS|CPU使用率|下雨|温度|堵车|服务状态|调用量).*",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern WRITE_PATTERN = Pattern.compile(
            ".*(帮我写|润色|文案|文章|邮件|周报|写一篇|写一段).*",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern DATA_PATTERN = Pattern.compile(
            ".*(统计|分析数据|表格|excel|csv|百分比|图表|计算).*",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern DEBATE_PATTERN = Pattern.compile(
            ".*(会不会|是否会|利弊|优缺点|争议|你觉得|看法|应不应该|值不值得|该不该|好不好).*",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern TECH_COMPARE_PATTERN = Pattern.compile(
            ".*(vs\\.?|对比|选型|哪个(更|好)|还是用|比较.*(框架|库|工具|语言|数据库|方案)).*",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern TECH_DESIGN_PATTERN = Pattern.compile(
            ".*(架构|方案设计|系统设计|如何设计|怎么设计|技术选型|拆解|规划|怎么实现|如何实现).*",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern TRANSLATE_PATTERN = Pattern.compile(
            ".*(翻译(成|为|一下)|translate|中译英|英译中|[^一-龥]翻译).*",
            Pattern.CASE_INSENSITIVE
    );

    @Override
    public IntentResult route(AskRequest askRequest) {
        if (askRequest == null || askRequest.getQuestion().isBlank()) {
            return ruleResult(IntentTypeEnum.UNKNOWN, 0.0, "输入为空");
        }
        String q = askRequest.getQuestion().trim();

        // Intercepting intents return immediately and do not enter the funnel.
        if (SWITCH_PLAYER_PATTERN.matcher(q).matches() || CLEAR_PATTERN.matcher(q).matches()
                || EXPORT_PATTERN.matcher(q).matches()) {
            return ruleResult(IntentTypeEnum.PLAYER_CONTROL, 0.95, "命中控制指令");
        }
        if (MULTI_AGENT_PATTERN.matcher(q).matches()) {
            return ruleResult(IntentTypeEnum.MULTI_AGENT_TASK, 0.85, "命中多Player协作关键词");
        }
        if (OFF_TOPIC_PATTERN.matcher(q).matches()) {
            return ruleResult(IntentTypeEnum.OFF_TOPIC_CHAT, 0.95, "命中闲聊问候");
        }

        // Rules inside the funnel.
        if (TECH_COMPARE_PATTERN.matcher(q).matches()) {
            return ruleResult(IntentTypeEnum.TECH_COMPARE, 0.82, "命中技术对比关键词");
        }
        if (TECH_DESIGN_PATTERN.matcher(q).matches()) {
            return ruleResult(IntentTypeEnum.TECH_DESIGN, 0.8, "命中架构/方案设计关键词");
        }
        if (CODE_PATTERN.matcher(q).matches()) {
            return ruleResult(IntentTypeEnum.CODE, 0.85, "命中代码相关关键词");
        }
        if (CHAT_PATTERN.matcher(q).matches()) {
            return ruleResult(IntentTypeEnum.CHAT, 0.85, "命中闲聊关键词");
        }
        if (SEARCH_PATTERN.matcher(q).matches()) {
            return ruleResult(IntentTypeEnum.SEARCH, 0.8, "命中搜索/实时信息关键词");
        }
        if (TRANSLATE_PATTERN.matcher(q).matches()) {
            return ruleResult(IntentTypeEnum.TRANSLATE, 0.85, "命中翻译关键词");
        }
        if (WRITE_PATTERN.matcher(q).matches()) {
            return ruleResult(IntentTypeEnum.WRITE, 0.8, "命中写作相关关键词");
        }
        if (DATA_PATTERN.matcher(q).matches()) {
            return ruleResult(IntentTypeEnum.DATA, 0.8, "命中数据分析关键词");
        }
        if (DEBATE_PATTERN.matcher(q).matches()) {
            return ruleResult(IntentTypeEnum.DEBATE, 0.8, "命中辩论/观点对比关键词");
        }

        return ruleResult(IntentTypeEnum.UNKNOWN, 0.3, "规则未命中");
    }

    private IntentResult ruleResult(IntentTypeEnum type, double confidence, String reason) {
        return new IntentResult(type, confidence, reason, IntentSource.RULE, null, null);
    }
}
