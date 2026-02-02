package com.travel.agent.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * AI 服务提供商配置
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "ai")
public class AIProviderConfig {
    
    /**
     * 主要 AI 提供商（gemini 或 openai）
     */
    private String primaryProvider = "gemini";
    
    /**
     * 备用 AI 提供商
     */
    private String fallbackProvider = "openai";
    
    /**
     * 是否启用降级
     */
    private Boolean enableFallback = true;
}
