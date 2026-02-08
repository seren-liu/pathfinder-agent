package com.travel.agent.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Agent 配置类
 *
 * 用于管理 ReAct Agent 的所有可配置参数，避免硬编码
 *
 * @author Pathfinder Team
 * @since 2026-02-08
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "agent.react")
public class AgentConfig {

    // ========== 执行控制 ==========

    /**
     * Agent 最大迭代次数
     * 防止无限循环，默认 15 次
     */
    private int maxIterations = 15;

    /**
     * Agent 总执行超时时间（秒）
     * 防止整体执行时间过长，默认 90 秒
     */
    private Duration executionTimeout = Duration.ofSeconds(90);

    /**
     * 单个工具执行超时时间（秒）
     * 防止单个工具卡死，默认 30 秒
     */
    private Duration toolExecutionTimeout = Duration.ofSeconds(30);

    /**
     * LLM 调用超时时间（秒）
     * 防止 LLM API 卡死，默认 20 秒
     */
    private Duration llmTimeout = Duration.ofSeconds(20);

    // ========== 循环检测 ==========

    /**
     * 循环检测阈值
     * 连续执行相同动作多少次视为循环，默认 3 次
     */
    private int loopDetectionThreshold = 3;

    /**
     * 历史记录窗口大小
     * 在 Prompt 中展示最近 N 条历史，默认 3 条
     */
    private int historyWindowSize = 3;

    // ========== 状态管理 ==========

    /**
     * 对话历史保留条数
     * 防止状态过大，默认保留 20 条
     */
    private int conversationHistoryLimit = 20;

    /**
     * 状态缓存过期时间（小时）
     * Redis 中状态的 TTL，默认 1 小时
     */
    private Duration stateCacheTtl = Duration.ofHours(1);

    // ========== 输入验证 ==========

    /**
     * 用户消息最小长度
     * 防止空消息或无意义输入，默认 1 个字符
     */
    private int messageMinLength = 1;

    /**
     * 用户消息最大长度
     * 防止超长输入占用资源，默认 2000 个字符
     */
    private int messageMaxLength = 2000;

    /**
     * 是否启用输入内容过滤
     * 过滤恶意脚本、SQL 注入等，默认启用
     */
    private boolean enableInputSanitization = true;

    /**
     * 是否启用 Prompt 注入防护
     * 转义用户输入中的特殊字符，默认启用
     */
    private boolean enablePromptInjectionProtection = true;

    /**
     * 是否启用日志脱敏
     * 隐藏日志中的敏感信息（邮箱、手机号等），默认启用
     */
    private boolean enableLogMasking = true;

    // ========== 性能优化 ==========

    /**
     * 是否启用 LLM 响应缓存
     * 缓存相同 Prompt 的响应，默认关闭（实验性功能）
     */
    private boolean enableLlmResponseCache = false;

    /**
     * LLM 响应缓存过期时间（分钟）
     * 默认 5 分钟
     */
    private Duration llmCacheTtl = Duration.ofMinutes(5);

    // ========== 监控与调试 ==========

    /**
     * 是否启用详细日志
     * 记录完整的 Prompt 和响应，生产环境建议关闭，默认 true
     */
    private boolean enableVerboseLogging = true;

    /**
     * 是否记录完整的推理历史
     * 保存所有 ReAct 步骤到响应中，默认 true
     */
    private boolean includeReasoningHistory = true;

    /**
     * Prompt 日志截断长度
     * 当 verboseLogging=false 时，日志中 Prompt 的最大长度，默认 200
     */
    private int promptLogTruncateLength = 200;
}
