package com.example.skill.controller;

import com.example.skill.service.SkillExecutionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class SkillController {

    private final SkillExecutionService executionService;

    @GetMapping("/skills")
    public ResponseEntity<List<Map<String, String>>> listSkills() {
        return ResponseEntity.ok(executionService.listSkills());
    }

    @GetMapping("/agents")
    public ResponseEntity<List<Map<String, String>>> listAgents() {
        return ResponseEntity.ok(executionService.listAgents());
    }

    /**
     * 上传 Skill 或 Agent
     *
     * @param type "skill" 或 "agent"
     * @param name 名称（文件夹名）
     * @param file SKILL.md 文件
     */
    @PostMapping("/upload")
    public ResponseEntity<Map<String, String>> upload(
            @RequestParam(defaultValue = "skill") String type,
            @RequestParam String name,
            @RequestParam("file") MultipartFile file) throws IOException {

        if (file.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("status", "error", "message", "上传的文件为空，请重新选择文件"));
        }
        String content = new String(file.getBytes(), StandardCharsets.UTF_8);
        executionService.upload(type, name, content);

        Map<String, String> resp = new LinkedHashMap<>();
        resp.put("status", "ok");
        resp.put("message", type + " [" + name + "] 上传成功");
        return ResponseEntity.ok(resp);
    }

    /**
     * 删除 Skill 或 Agent
     */
    @DeleteMapping("/remove")
    public ResponseEntity<Map<String, String>> remove(
            @RequestParam(defaultValue = "skill") String type,
            @RequestParam String name) throws IOException {

        executionService.delete(type, name);

        Map<String, String> resp = new LinkedHashMap<>();
        resp.put("status", "ok");
        resp.put("message", type + " [" + name + "] 已删除");
        return ResponseEntity.ok(resp);
    }

    @PostMapping("/execute")
    public ResponseEntity<Map<String, Object>> execute(
            @RequestParam String skillName,
            @RequestParam String input) {

        String result = executionService.execute(skillName, input, null);
        return ResponseEntity.ok(buildResult(skillName, result));
    }

    @PostMapping("/execute/upload")
    public ResponseEntity<Map<String, Object>> executeWithFile(
            @RequestParam String skillName,
            @RequestParam String input,
            @RequestParam("file") MultipartFile file) throws IOException {

        if (file.isEmpty()) {
            Map<String, Object> err = new LinkedHashMap<>();
            err.put("skill", skillName);
            err.put("result", "执行失败：上传的文件为空，请重新选择文件");
            return ResponseEntity.badRequest().body(err);
        }

        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || originalFilename.isBlank()) {
            originalFilename = "upload_" + System.currentTimeMillis();
        }
        String filePath = executionService.saveUploadedFile(file.getBytes(), originalFilename);
        String result = executionService.execute(skillName, input, filePath);
        return ResponseEntity.ok(buildResult(skillName, result));
    }

    private Map<String, Object> buildResult(String skillName, String result) {
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("skill", skillName);
        resp.put("result", result);
        return resp;
    }
}
