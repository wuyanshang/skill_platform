package com.example.skill.service;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.skills.registry.SkillRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class SkillExecutionService {

    private final SkillRegistry skillRegistry;
    private final SkillRegistry agentRegistry;
    private final ReactAgent skillAgent;

    @Value("${skill.directory:./skills}")
    private String skillDirectory;

    @Value("${skill.agent-directory:./agents}")
    private String agentDirectory;

    public SkillExecutionService(SkillRegistry skillRegistry,
                                 @Qualifier("agentRegistry") SkillRegistry agentRegistry,
                                 ReactAgent skillAgent) {
        this.skillRegistry = skillRegistry;
        this.agentRegistry = agentRegistry;
        this.skillAgent = skillAgent;
    }

    public List<Map<String, String>> listSkills() {
        return toMetadataList(skillRegistry);
    }

    public List<Map<String, String>> listAgents() {
        return toMetadataList(agentRegistry);
    }

    /**
     * 上传 Skill 或 Agent 的 SKILL.md 文件。
     *
     * @param type     "skill" 或 "agent"
     * @param name     名称（将成为文件夹名）
     * @param content  SKILL.md 的文本内容
     */
    public void upload(String type, String name, String content) throws IOException {
        String baseDir = "agent".equals(type) ? agentDirectory : skillDirectory;
        Path dir = Path.of(baseDir, name);
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("SKILL.md"), content);
        log.info("已上传 {} [{}] 到 {}", type, name, dir.toAbsolutePath());

        reloadRegistry(type);
    }

    /**
     * 删除 Skill 或 Agent
     */
    public void delete(String type, String name) throws IOException {
        String baseDir = "agent".equals(type) ? agentDirectory : skillDirectory;
        Path dir = Path.of(baseDir, name);
        if (Files.exists(dir)) {
            Files.walk(dir)
                    .sorted(Comparator.reverseOrder())
                    .forEach(p -> {
                        try { Files.delete(p); } catch (IOException ignored) {}
                    });
            log.info("已删除 {} [{}]", type, name);
            reloadRegistry(type);
        }
    }

    /**
     * 执行指定 Skill。
     */
    public String execute(String skillName, String userInput, String filePath) {
        if (!skillRegistry.contains(skillName)) {
            return "Skill 不存在: " + skillName;
        }

        StringBuilder prompt = new StringBuilder();
        prompt.append("请使用 read_skill 工具读取名为 \"").append(skillName).append("\" 的技能，");
        prompt.append("然后按照技能的指令完成以下任务。\n\n");
        prompt.append("用户任务: ").append(userInput);

        if (filePath != null && !filePath.isBlank()) {
            prompt.append("\n\n待处理文件路径: ").append(filePath);
        }

        log.info("执行 Skill [{}]", skillName);

        try {
            Optional<OverAllState> result = skillAgent.invoke(prompt.toString());
            return result.map(this::extractAnswer).orElse("Agent 未返回结果");
        } catch (Exception e) {
            log.error("Skill [{}] 执行失败", skillName, e);
            return "执行失败: " + e.getMessage();
        }
    }

    public String saveUploadedFile(byte[] fileBytes, String originalFilename) throws IOException {
        Path uploadDir = Path.of("uploads");
        Files.createDirectories(uploadDir);
        Path filePath = uploadDir.resolve(originalFilename);
        Files.write(filePath, fileBytes);
        log.info("文件已保存: {}", filePath.toAbsolutePath());
        return filePath.toAbsolutePath().toString();
    }

    private void reloadRegistry(String type) {
        try {
            if ("agent".equals(type)) {
                agentRegistry.reload();
            } else {
                skillRegistry.reload();
            }
        } catch (UnsupportedOperationException e) {
            log.debug("Registry 不支持热加载，需要重启服务");
        }
    }

    private List<Map<String, String>> toMetadataList(SkillRegistry registry) {
        return registry.listAll().stream()
                .map(meta -> {
                    Map<String, String> info = new LinkedHashMap<>();
                    info.put("name", meta.name());
                    info.put("description", meta.description());
                    return info;
                })
                .collect(Collectors.toList());
    }

    private String extractAnswer(OverAllState state) {
        Optional<Object> messages = state.value("messages");
        if (messages.isPresent() && messages.get() instanceof List<?> msgList) {
            for (int i = msgList.size() - 1; i >= 0; i--) {
                if (msgList.get(i) instanceof AssistantMessage am) {
                    String text = am.getText();
                    if (text != null && !text.isBlank()) {
                        return text;
                    }
                }
            }
        }
        return state.data().toString();
    }
}
