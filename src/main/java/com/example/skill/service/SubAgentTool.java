package com.example.skill.service;

import com.alibaba.cloud.ai.graph.skills.registry.SkillRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.util.List;

/**
 * 子智能体工具：主 Agent 可以通过此工具启动一个子智能体来执行任务。
 * 子智能体的指令来自 agents/ 目录下的 SKILL.md。
 * <p>
 * 效果等价于 Lingma IDE 中的 task 工具调用子智能体。
 */
@Slf4j
public class SubAgentTool {

    private final ChatModel chatModel;
    private final SkillRegistry agentRegistry;

    public SubAgentTool(ChatModel chatModel, SkillRegistry agentRegistry) {
        this.chatModel = chatModel;
        this.agentRegistry = agentRegistry;
    }

    @Tool(description = "启动一个子智能体来执行任务。子智能体会加载 agents 目录中对应名称的指令文件，"
            + "按照指令自主完成任务并返回结果。适用于需要专业能力的子任务，如歧义检测、数据质量检查等。"
            + "可以多次调用此工具来并行处理不同批次的数据。")
    public String spawnAgent(
            @ToolParam(description = "子智能体名称，对应 agents 目录下的文件夹名") String agentName,
            @ToolParam(description = "交给子智能体处理的具体任务内容") String taskInput) {

        log.info("启动子智能体 [{}]，输入长度: {} 字符", agentName, taskInput.length());

        if (!agentRegistry.contains(agentName)) {
            List<String> names = agentRegistry.listAll().stream()
                    .map(m -> m.getName()).toList();
            String available = String.join(", ", names);
            return "子智能体不存在: " + agentName + "。可用子智能体: "
                    + (available.isEmpty() ? "（无）" : available);
        }

        try {
            String agentInstruction = agentRegistry.readSkillContent(agentName);
            String result = ChatClient.builder(chatModel)
                    .build()
                    .prompt()
                    .system(agentInstruction)
                    .user(taskInput)
                    .call()
                    .content();

            log.info("子智能体 [{}] 执行完成，输出长度: {} 字符", agentName, result.length());
            return result;
        } catch (Exception e) {
            log.error("子智能体 [{}] 执行失败", agentName, e);
            return "子智能体执行失败: " + e.getMessage();
        }
    }
}
