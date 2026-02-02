package com.travel.agent.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Geoapify Places API 配置
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "geoapify")
public class GeoapifyConfig {
    
    private String apiKey;
    private String baseUrl = "https://api.geoapify.com/v2";
    private Integer timeout = 10000;
}
