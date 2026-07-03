package com.showengine.service.impl;

import com.showengine.enums.PlayerEnum;
import com.showengine.players.enums.PlayerStatusEnum;
import com.showengine.model.SubTask;
import com.showengine.service.DecomposeService;
import com.showengine.players.service.PlayerService;
import com.showengine.utils.PromptTemplates;
import com.showengine.utils.JacksonUtil;
import com.showengine.utils.PlayerFactory;
import com.showengine.utils.PlayerUtils;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class DecomposeServiceImpl implements DecomposeService {

    private final PlayerFactory playerFactory;

    @Override
    public List<SubTask> decompose(String conversationId, String question, PlayerEnum master) {
        // Build the slave list from the two Players other than the Master.
        List<String> slaves = PlayerUtils.listAllPlayers().stream()
                .filter(p -> p != master)
                .map(PlayerEnum::name)
                .toList();
        String slave1 = slaves.get(0);
        String slave2 = slaves.get(1);

        // Call the Master LLM and collect the full response.
        PlayerService masterService = playerFactory.get(master);
        String subConvId = UUID.nameUUIDFromBytes((conversationId + "-decompose").getBytes()).toString();
        StringBuilder buf = new StringBuilder();

        masterService.ask(subConvId, PromptTemplates.executeDecompose(question, master.name(), slave1, slave2),
                chunk -> {
                    if (chunk.getStatus() == PlayerStatusEnum.STREAMING) {
                        buf.append(chunk.getContent());
                    }
                });

        return parseSubTasks(buf.toString().trim());
    }

    /**
     * Locates a JSON array in raw LLM output and parses it into a SubTask list.
     * The LLM may wrap JSON with explanatory text, so brackets are used as boundaries.
     */
    private List<SubTask> parseSubTasks(String raw) {
        int start = raw.indexOf('[');
        int end   = raw.lastIndexOf(']');
        if (start < 0 || end <= start) {
            log.error("DecomposeService：响应中未找到 JSON 数组，原始输出：{}", raw);
            throw new IllegalStateException("任务分解失败：LLM 未返回合法 JSON 数组");
        }

        try {
            JsonNode node = JacksonUtil.toJsonNode(raw.substring(start, end + 1));
            List<SubTask> subtasks = JacksonUtil.toListWithEmptyDefault(
                    raw.substring(start, end + 1), SubTask.class);
            if (subtasks.isEmpty()) {
                throw new IllegalStateException("任务分解失败：解析结果为空列表");
            }
            log.info("DecomposeService：分解出 {} 个子任务", subtasks.size());
            return subtasks;
        } catch (Exception e) {
            log.error("DecomposeService：JSON 解析失败，原始输出：{}", raw, e);
            throw new IllegalStateException("任务分解 JSON 解析失败：" + e.getMessage(), e);
        }
    }
}
