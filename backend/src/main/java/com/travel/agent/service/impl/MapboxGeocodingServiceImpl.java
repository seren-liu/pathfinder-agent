package com.travel.agent.service.impl;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.travel.agent.config.MapboxConfig;
import com.travel.agent.service.MapboxGeocodingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class MapboxGeocodingServiceImpl implements MapboxGeocodingService {

    private final MapboxConfig mapboxConfig;
    private final Gson gson = new Gson();
    private OkHttpClient client;

    /**
     * 初始化 HTTP 客户端（懒加载）
     */
    private OkHttpClient getClient() {
        if (client == null) {
            client = new OkHttpClient.Builder()
                    .connectTimeout(mapboxConfig.getTimeout(), TimeUnit.MILLISECONDS)
                    .readTimeout(mapboxConfig.getTimeout(), TimeUnit.MILLISECONDS)
                    .writeTimeout(mapboxConfig.getTimeout(), TimeUnit.MILLISECONDS)
                    .build();
        }
        return client;
    }

    /**
     * 地理编码：将地址转换为经纬度坐标
     * 使用 Spring Cache 缓存结果（避免重复请求）
     */
    @Override
    @Cacheable(value = "mapboxGeocode", key = "#address", sync = true, condition = "#address != null && !#address.trim().isEmpty()")
    public Map<String, BigDecimal> geocodeAddress(String address) {
        if (address == null || address.trim().isEmpty()) {
            log.warn("Geocoding failed: address is empty");
            return null;
        }

        try {
            // URL 编码地址
            String encodedAddress = URLEncoder.encode(address.trim(), StandardCharsets.UTF_8);
            
            // 构建 Mapbox Geocoding API URL
            // 格式: https://api.mapbox.com/geocoding/v5/mapbox.places/{search_text}.json
            String url = String.format(
                    "%s/%s.json?access_token=%s&limit=1&types=place,address,poi",
                    mapboxConfig.getGeocodingUrl(),
                    encodedAddress,
                    mapboxConfig.getAccessToken()
            );

            log.debug("🗺️ Mapbox Geocoding request: {}", address);

            // 发送 HTTP 请求
            Request request = new Request.Builder()
                    .url(url)
                    .get()
                    .build();

            try (Response response = getClient().newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    log.warn("Mapbox Geocoding failed: status={}, address={}", response.code(), address);
                    return null;
                }

                if (response.body() == null) {
                    log.warn("Mapbox Geocoding failed: empty response body");
                    return null;
                }

                String responseBody = response.body().string();
                JsonObject jsonResponse = gson.fromJson(responseBody, JsonObject.class);

                // 解析响应
                JsonArray features = jsonResponse.getAsJsonArray("features");
                if (features == null || features.size() == 0) {
                    log.debug("Mapbox Geocoding: no results found for address: {}", address);
                    return null;
                }

                // 获取第一个结果的坐标
                JsonObject firstFeature = features.get(0).getAsJsonObject();
                JsonObject geometry = firstFeature.getAsJsonObject("geometry");
                
                if (geometry == null || !geometry.has("coordinates")) {
                    log.warn("Mapbox Geocoding: invalid geometry in response");
                    return null;
                }

                JsonArray coordinates = geometry.getAsJsonArray("coordinates");
                if (coordinates.size() < 2) {
                    log.warn("Mapbox Geocoding: invalid coordinates array");
                    return null;
                }

                // Mapbox 返回格式: [longitude, latitude]
                BigDecimal longitude = coordinates.get(0).getAsBigDecimal();
                BigDecimal latitude = coordinates.get(1).getAsBigDecimal();

                Map<String, BigDecimal> result = new HashMap<>();
                result.put("latitude", latitude);
                result.put("longitude", longitude);

                log.debug("✅ Mapbox Geocoding success: {} -> lat={}, lon={}", 
                    address, latitude, longitude);

                return result;
            }

        } catch (IOException e) {
            log.error("Mapbox Geocoding error for address: {}", address, e);
            return null;
        } catch (Exception e) {
            log.error("Unexpected error during Mapbox Geocoding: {}", address, e);
            return null;
        }
    }

    /**
     * 批量地理编码（并行处理，带缓存）
     */
    @Override
    public Map<String, Map<String, BigDecimal>> batchGeocode(List<String> addresses) {
        Map<String, Map<String, BigDecimal>> results = new HashMap<>();
        
        if (addresses == null || addresses.isEmpty()) {
            return results;
        }

        log.info("🗺️ Batch geocoding {} addresses using Mapbox", addresses.size());
        
        // 使用并行流处理（利用缓存避免重复请求）
        addresses.parallelStream().forEach(address -> {
            try {
                Map<String, BigDecimal> coords = geocodeAddress(address);
                if (coords != null) {
                    synchronized (results) {
                        results.put(address, coords);
                    }
                }
            } catch (Exception e) {
                log.error("Failed to geocode address: {}", address, e);
            }
        });

        log.info("✅ Batch geocoding completed: {}/{} successful", 
            results.size(), addresses.size());

        return results;
    }
}
