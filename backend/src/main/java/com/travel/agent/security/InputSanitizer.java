package com.travel.agent.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/**
 * 输入净化工具类
 *
 * 用于防止恶意输入、SQL 注入、XSS 攻击、LLM Prompt 注入等安全问题
 *
 * @author Pathfinder Team
 * @since 2026-02-08
 */
@Slf4j
@Component
public class InputSanitizer {

    // ========== 危险模式定义 ==========

    /**
     * SQL 注入关键词模式
     * 匹配常见的 SQL 注入尝试
     */
    private static final Pattern SQL_INJECTION_PATTERN = Pattern.compile(
            "(?i)(\\b(SELECT|INSERT|UPDATE|DELETE|DROP|CREATE|ALTER|EXEC|EXECUTE|UNION|SCRIPT)\\b.*\\b(FROM|INTO|TABLE|DATABASE)\\b)|" +
            "(--|;|'|\")|" +
            "(\\bOR\\b.*=.*)|" +
            "(\\bAND\\b.*=.*)"
    );

    /**
     * XSS 脚本注入模式
     * 匹配常见的脚本标签
     */
    private static final Pattern XSS_PATTERN = Pattern.compile(
            "(?i)<script[^>]*>.*?</script>|" +
            "<iframe[^>]*>.*?</iframe>|" +
            "javascript:|" +
            "onerror\\s*=|" +
            "onload\\s*="
    );

    /**
     * LLM Prompt 注入模式
     * 匹配试图劫持 AI 行为的指令
     */
    private static final Pattern PROMPT_INJECTION_PATTERN = Pattern.compile(
            "(?i)(ignore\\s+(previous|all)\\s+(instructions?|prompts?|rules?))|" +
            "(forget\\s+(everything|all|previous))|" +
            "(you\\s+are\\s+now)|" +
            "(system\\s*:\\s*)|" +
            "(new\\s+instructions?\\s*:)|" +
            "(disregard\\s+(previous|above))"
    );

    /**
     * 控制字符模式
     * 匹配不可见的控制字符（除了常见的空白字符）
     */
    private static final Pattern CONTROL_CHARS_PATTERN = Pattern.compile(
            "[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F\\x7F]"
    );

    /**
     * 电子邮件地址模式（用于日志脱敏）
     */
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "\\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Z|a-z]{2,}\\b"
    );

    /**
     * 手机号码模式（用于日志脱敏）
     * 支持中国、美国、澳大利亚等常见格式
     */
    private static final Pattern PHONE_PATTERN = Pattern.compile(
            "\\b(\\+?\\d{1,3}[-.\\s]?)?\\(?\\d{3}\\)?[-.\\s]?\\d{3,4}[-.\\s]?\\d{4}\\b"
    );

    /**
     * 信用卡号模式（用于日志脱敏）
     */
    private static final Pattern CREDIT_CARD_PATTERN = Pattern.compile(
            "\\b\\d{4}[\\s-]?\\d{4}[\\s-]?\\d{4}[\\s-]?\\d{4}\\b"
    );

    // ========== 公共方法 ==========

    /**
     * 验证输入消息
     *
     * @param message 用户输入的消息
     * @param minLength 最小长度
     * @param maxLength 最大长度
     * @throws IllegalArgumentException 如果验证失败
     */
    public void validateMessage(String message, int minLength, int maxLength) {
        if (message == null) {
            throw new IllegalArgumentException("Message cannot be null");
        }

        String trimmed = message.trim();

        if (trimmed.length() < minLength) {
            throw new IllegalArgumentException(
                    String.format("Message too short. Minimum %d characters required.", minLength)
            );
        }

        if (trimmed.length() > maxLength) {
            throw new IllegalArgumentException(
                    String.format("Message too long. Maximum %d characters allowed.", maxLength)
            );
        }
    }

    /**
     * 净化用户输入
     *
     * 移除或转义危险字符和模式
     *
     * @param input 原始输入
     * @return 净化后的输入
     */
    public String sanitizeInput(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }

        String sanitized = input;

        // 1. 移除控制字符
        sanitized = CONTROL_CHARS_PATTERN.matcher(sanitized).replaceAll("");

        // 2. 检测并警告 SQL 注入尝试
        if (SQL_INJECTION_PATTERN.matcher(sanitized).find()) {
            log.warn("⚠️ Potential SQL injection detected in input: {}", truncate(input, 50));
            // 可以选择拒绝或净化
            sanitized = SQL_INJECTION_PATTERN.matcher(sanitized).replaceAll("[FILTERED]");
        }

        // 3. 检测并移除 XSS 脚本
        if (XSS_PATTERN.matcher(sanitized).find()) {
            log.warn("⚠️ Potential XSS script detected in input: {}", truncate(input, 50));
            sanitized = XSS_PATTERN.matcher(sanitized).replaceAll("[FILTERED]");
        }

        // 4. 检测并警告 Prompt 注入尝试
        if (PROMPT_INJECTION_PATTERN.matcher(sanitized).find()) {
            log.warn("⚠️ Potential prompt injection detected in input: {}", truncate(input, 50));
            // Prompt 注入较难完全过滤，记录警告但保留原文本
            // 后续通过 escapeForPrompt() 转义
        }

        // 5. 标准化空白字符
        sanitized = sanitized.replaceAll("\\s+", " ").trim();

        return sanitized;
    }

    /**
     * 为 Prompt 转义输入
     *
     * 防止用户输入破坏 Prompt 结构或注入恶意指令
     *
     * @param input 原始输入
     * @return 转义后的输入
     */
    public String escapeForPrompt(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }

        return input
                // 转义反斜杠（必须最先执行）
                .replace("\\", "\\\\")
                // 转义双引号
                .replace("\"", "\\\"")
                // 转义单引号
                .replace("'", "\\'")
                // 转义换行符（保留语义，但避免多行注入）
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                // 转义制表符
                .replace("\t", "\\t")
                // 移除 Unicode 控制字符
                .replaceAll("[\\p{C}&&[^\n\r\t]]", "");
    }

    /**
     * 为日志脱敏
     *
     * 隐藏敏感信息（邮箱、手机号、信用卡等）
     *
     * @param text 原始文本
     * @return 脱敏后的文本
     */
    public String maskForLog(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }

        String masked = text;

        // 脱敏邮箱
        masked = EMAIL_PATTERN.matcher(masked).replaceAll("[EMAIL]");

        // 脱敏手机号
        masked = PHONE_PATTERN.matcher(masked).replaceAll("[PHONE]");

        // 脱敏信用卡号
        masked = CREDIT_CARD_PATTERN.matcher(masked).replaceAll("[CARD]");

        return masked;
    }

    /**
     * 检测是否包含潜在的恶意内容
     *
     * @param input 输入文本
     * @return true 如果检测到恶意内容
     */
    public boolean containsMaliciousContent(String input) {
        if (input == null || input.isEmpty()) {
            return false;
        }

        return SQL_INJECTION_PATTERN.matcher(input).find() ||
               XSS_PATTERN.matcher(input).find() ||
               PROMPT_INJECTION_PATTERN.matcher(input).find();
    }

    /**
     * 截断文本（用于日志输出）
     *
     * @param text 原始文本
     * @param maxLength 最大长度
     * @return 截断后的文本
     */
    public String truncate(String text, int maxLength) {
        if (text == null) {
            return null;
        }

        if (text.length() <= maxLength) {
            return text;
        }

        return text.substring(0, maxLength) + "...";
    }

    /**
     * 验证并净化输入（组合方法）
     *
     * 先验证长度，再净化内容
     *
     * @param input 原始输入
     * @param minLength 最小长度
     * @param maxLength 最大长度
     * @return 净化后的输入
     * @throws IllegalArgumentException 如果验证失败
     */
    public String validateAndSanitize(String input, int minLength, int maxLength) {
        validateMessage(input, minLength, maxLength);
        return sanitizeInput(input);
    }
}
