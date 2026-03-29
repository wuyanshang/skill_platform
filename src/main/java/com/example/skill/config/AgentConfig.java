package com.example.skill.config;

import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.agent.hook.shelltool.ShellToolAgentHook;
import com.alibaba.cloud.ai.graph.agent.hook.skills.SkillsAgentHook;
import com.alibaba.cloud.ai.graph.agent.tools.PythonTool;
import com.alibaba.cloud.ai.graph.agent.tools.ShellTool2;
import com.alibaba.cloud.ai.graph.checkpoint.savers.MemorySaver;
import com.alibaba.cloud.ai.graph.skills.registry.SkillRegistry;
import com.alibaba.cloud.ai.graph.skills.registry.filesystem.FileSystemSkillRegistry;
import com.example.skill.service.SubAgentTool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallbacks;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@Slf4j
@Configuration
public class AgentConfig {

    @Value("${skill.directory:./skills}")
    private String skillDirectory;

    @Value("${skill.agent-directory:./agents}")
    private String agentDirectory;

    @Value("${skill.work-directory:.}")
    private String workDirectory;

    /**
     * Skill 注册表：用户页面上看到的可选 Skill 列表。
     */
    @Bean
    public SkillRegistry skillRegistry() throws IOException {
        Files.createDirectories(Path.of(skillDirectory));
        SkillRegistry registry = FileSystemSkillRegistry.builder()
                .projectSkillsDirectory(skillDirectory)
                .build();
        log.info("Skill 注册表已加载 {} 个，目录: {}", registry.size(), skillDirectory);
        return registry;
    }

    /**
     * Agent 注册表：存放可被 spawn_agent 工具调用的子智能体定义。
     */
    @Bean("agentRegistry")
    public SkillRegistry agentRegistry() throws IOException {
        Files.createDirectories(Path.of(agentDirectory));
        SkillRegistry registry = FileSystemSkillRegistry.builder()
                .projectSkillsDirectory(agentDirectory)
                .build();
        log.info("Agent 注册表已加载 {} 个，目录: {}", registry.size(), agentDirectory);
        return registry;
    }

    @Bean
    public SubAgentTool subAgentTool(ChatModel chatModel,
                                     @Qualifier("agentRegistry") SkillRegistry agentRegistry) {
        return new SubAgentTool(chatModel, agentRegistry);
    }

    @Bean
    public SkillsAgentHook skillsAgentHook(SkillRegistry skillRegistry) {
        return SkillsAgentHook.builder()
                .skillRegistry(skillRegistry)
                .autoReload(true)
                .build();
    }

    @Bean
    public ShellToolAgentHook shellToolAgentHook() {
        return ShellToolAgentHook.builder()
                .shellTool2(ShellTool2.builder(workDirectory).build())
                .build();
    }

    /**
     * 通用 ReactAgent，拥有以下能力：
     * - read_skill：读取 skills/ 目录中的 Skill 指令（由 SkillsAgentHook 提供）
     * - spawn_agent：启动 agents/ 目录中的子智能体（由 SubAgentTool 提供）
     * - python：执行 Python 脚本（由 PythonTool 提供）
     * - shell：执行 Shell 命令（由 ShellToolAgentHook 提供）
     */
    @Bean
    public ReactAgent skillAgent(ChatModel chatModel,
                                 SkillsAgentHook skillsHook,
                                 ShellToolAgentHook shellHook,
                                 SubAgentTool subAgentTool) {
        return ReactAgent.builder()
                .name("skill-agent")
                .model(chatModel)
                .saver(new MemorySaver())
                .tools(PythonTool.createPythonToolCallback(PythonTool.DESCRIPTION),
                       ToolCallbacks.from(subAgentTool))
                .hooks(List.of(skillsHook, shellHook))
                .enableLogging(true)
                .build();
    }
}
