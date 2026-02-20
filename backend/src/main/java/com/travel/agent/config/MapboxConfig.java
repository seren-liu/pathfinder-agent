package com.travel.agent.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Mapbox API 配置
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "mapbox")
public class MapboxConfig {
    
    /**
     * Mapbox Access Token
     */
    private String accessToken;
    
    /**
     * Geocoding API 基础 URL
     */
    private String geocodingUrl = "https://api.mapbox.com/geocoding/v5/mapbox.places";
    
    /**
     * Matrix API 基础 URL
     */
    private String matrixUrl = "https://api.mapbox.com/directions-matrix/v1/mapbox/driving";

    /**
     * 请求超时时间（毫秒）
     */
    private Integer timeout = 15000;
}
