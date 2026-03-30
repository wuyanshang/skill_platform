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

    @Bean
    public SkillRegistry skillRegistry() throws IOException {
        Files.createDirectories(Path.of(skillDirectory));
        SkillRegistry registry = FileSystemSkillRegistry.builder()
                .projectSkillsDirectory(skillDirectory)
                .build();
        log.info("Skill 注册表已加载 {} 个，目录: {}", registry.size(), skillDirectory);
        return registry;
    }

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

    @Bean
    public ReactAgent skillAgent(ChatModel chatModel,
                                 SkillsAgentHook skillsHook,
                                 ShellToolAgentHook shellHook,
                                 SubAgentTool subAgentTool) {
        return ReactAgent.builder()
                .name("skill-agent")
                .model(chatModel)
                .saver(new MemorySaver())
                .tools(PythonTool.createPythonToolCallback(PythonTool.DESCRIPTION))
                .methodTools(subAgentTool)
                .hooks(List.of(skillsHook, shellHook))
                .enableLogging(true)
                .build();
    }
}
