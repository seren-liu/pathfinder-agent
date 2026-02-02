package com.travel.agent.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAI API 配置
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "openai")
public class OpenAIConfig {
    
    private String apiKey;
    private String model = "gpt-4o-mini";
    private String baseUrl = "https://api.openai.com/v1";
    private Integer timeout = 30000;  // 降低到 30 秒
    private Integer maxTokens = 1500;  // 降低到 1500（推荐生成约 1200 tokens）
    private Double temperature = 0.7;
}
