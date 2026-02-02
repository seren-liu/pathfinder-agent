package com.travel.agent.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Google Gemini API 配置
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "gemini")
public class GeminiConfig {
    
    /**
     * Gemini API Key
     */
    private String apiKey;
    
    /**
     * 模型名称（推荐 gemini-1.5-flash）
     */
    private String model = "gemini-1.5-flash";
    
    /**
     * API 基础 URL
     */
    private String baseUrl = "https://generativelanguage.googleapis.com/v1beta";
    
    /**
     * 请求超时时间（毫秒）
     */
    private Integer timeout = 20000;
    
    /**
     * 最大 Token 数
     */
    private Integer maxTokens = 8192;
    
    /**
     * 温度参数（0.0-1.0）
     */
    private Double temperature = 0.7;
}
