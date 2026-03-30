package com.example.skill.interceptor;

import com.alibaba.cloud.ai.graph.agent.interceptor.ToolCallHandler;
import com.alibaba.cloud.ai.graph.agent.interceptor.ToolCallRequest;
import com.alibaba.cloud.ai.graph.agent.interceptor.ToolCallResponse;
import com.alibaba.cloud.ai.graph.agent.interceptor.ToolInterceptor;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Shell 命令黑名单拦截器。
 * 在 shell 工具执行前检查命令内容，命中黑名单则直接拒绝。
 */
@Slf4j
public class ShellCommandInterceptor extends ToolInterceptor {

    private final List<Pattern> forbiddenPatterns;

    /**
     * @param forbiddenCommands 禁止的命令关键词列表，如 ["rm -rf", "shutdown", "reboot", "format"]。
     *                          会被编译为正则，忽略大小写，匹配命令中任意位置。
     */
    public ShellCommandInterceptor(List<String> forbiddenCommands) {
        this.forbiddenPatterns = forbiddenCommands.stream()
                .map(cmd -> Pattern.compile(Pattern.quote(cmd), Pattern.CASE_INSENSITIVE))
                .collect(Collectors.toList());
    }

    @Override
    public ToolCallResponse interceptToolCall(ToolCallRequest request, ToolCallHandler handler) {
        if (!"shell".equals(request.getToolName())) {
            return handler.call(request);
        }

        String arguments = request.getArguments();
        for (Pattern pattern : forbiddenPatterns) {
            if (pattern.matcher(arguments).find()) {
                String blocked = pattern.pattern();
                log.warn("Shell 命令被拦截，命中黑名单 [{}]，原始参数: {}", blocked, arguments);
                return ToolCallResponse.error(
                        request.getToolCallId(),
                        request.getToolName(),
                        "命令被禁止执行，包含受限操作: " + blocked);
            }
        }

        return handler.call(request);
    }
}
