package com.travel.agent.service.impl;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.travel.agent.config.GeminiConfig;
import com.travel.agent.exception.BusinessException;
import com.travel.agent.service.GeminiService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class GeminiServiceImpl implements GeminiService {

    private final GeminiConfig geminiConfig;
    private final Gson gson = new Gson();
    private OkHttpClient client;

    /**
     * 初始化 HTTP 客户端（懒加载）
     */
    private OkHttpClient getClient() {
        if (client == null) {
            client = new OkHttpClient.Builder()
                    .connectTimeout(geminiConfig.getTimeout(), TimeUnit.MILLISECONDS)
                    .readTimeout(geminiConfig.getTimeout(), TimeUnit.MILLISECONDS)
                    .writeTimeout(geminiConfig.getTimeout(), TimeUnit.MILLISECONDS)
                    .build();
        }
        return client;
    }

    /**
     * 调用 Gemini API
     */
    @Override
    public String chat(String prompt) {
        if (prompt == null || prompt.trim().isEmpty()) {
            throw new BusinessException("Prompt cannot be empty");
        }

        try {
            long startTime = System.currentTimeMillis();
            log.info("🤖 Calling Gemini API...");

            // 构建 Gemini API 请求体
            JsonObject requestBody = buildGeminiRequest(prompt);

            // 构建 URL（包含 API Key）
            String url = String.format(
                    "%s/models/%s:generateContent?key=%s",
                    geminiConfig.getBaseUrl(),
                    geminiConfig.getModel(),
                    geminiConfig.getApiKey()
            );

            // 构建 HTTP 请求
            Request request = new Request.Builder()
                    .url(url)
                    .header("Content-Type", "application/json")
                    .post(RequestBody.create(
                            requestBody.toString(),
                            MediaType.parse("application/json")
                    ))
                    .build();

            // 执行请求
            try (Response response = getClient().newCall(request).execute()) {
                long duration = System.currentTimeMillis() - startTime;

                if (!response.isSuccessful()) {
                    String errorBody = response.body() != null ? response.body().string() : "Unknown error";
                    log.error("❌ Gemini API error: status={}, body={}", response.code(), errorBody);
                    throw new BusinessException("Gemini API error: " + response.code());
                }

                if (response.body() == null) {
                    throw new BusinessException("Gemini API returned empty response");
                }

                String responseBody = response.body().string();
                String aiResponse = parseGeminiResponse(responseBody);

                log.info("✅ Gemini API success: duration={}ms, response_length={}", 
                    duration, aiResponse.length());

                return aiResponse;
            }

        } catch (IOException e) {
            log.error("❌ Gemini API network error", e);
            throw new BusinessException("Failed to connect to Gemini API. Please try again.");
        } catch (Exception e) {
            log.error("❌ Gemini API unexpected error", e);
            throw new BusinessException("Gemini API error: " + e.getMessage());
        }
    }

    /**
     * 构建 Gemini API 请求体
     */
    private JsonObject buildGeminiRequest(String prompt) {
        JsonObject requestBody = new JsonObject();

        // 设置生成配置
        JsonObject generationConfig = new JsonObject();
        generationConfig.addProperty("temperature", geminiConfig.getTemperature());
        generationConfig.addProperty("maxOutputTokens", geminiConfig.getMaxTokens());
        requestBody.add("generationConfig", generationConfig);

        // 构建内容
        JsonObject part = new JsonObject();
        part.addProperty("text", prompt);

        JsonObject content = new JsonObject();
        content.addProperty("role", "user");
        content.add("parts", gson.toJsonTree(new JsonObject[]{part}));

        JsonArray contents = new JsonArray();
        contents.add(content);
        requestBody.add("contents", contents);

        return requestBody;
    }

    /**
     * 解析 Gemini API 响应
     */
    private String parseGeminiResponse(String responseBody) {
        try {
            JsonObject jsonResponse = gson.fromJson(responseBody, JsonObject.class);

            // Gemini 响应格式：
            // {
            //   "candidates": [
            //     {
            //       "content": {
            //         "parts": [
            //           {"text": "AI response here"}
            //         ]
            //       }
            //     }
            //   ]
            // }

            JsonArray candidates = jsonResponse.getAsJsonArray("candidates");
            if (candidates == null || candidates.size() == 0) {
                throw new BusinessException("Gemini returned no candidates");
            }

            JsonObject firstCandidate = candidates.get(0).getAsJsonObject();
            JsonObject content = firstCandidate.getAsJsonObject("content");
            JsonArray parts = content.getAsJsonArray("parts");

            if (parts == null || parts.size() == 0) {
                throw new BusinessException("Gemini returned no content parts");
            }

            JsonObject firstPart = parts.get(0).getAsJsonObject();
            String text = firstPart.get("text").getAsString();

            if (text == null || text.trim().isEmpty()) {
                throw new BusinessException("Gemini returned empty text");
            }

            return text.trim();

        } catch (Exception e) {
            log.error("Failed to parse Gemini response: {}", responseBody, e);
            throw new BusinessException("Failed to parse Gemini response: " + e.getMessage());
        }
    }

    /**
     * 检查 Gemini 服务是否可用
     */
    @Override
    public boolean isAvailable() {
        try {
            // 发送简单的测试请求
            String testResponse = chat("Hello");
            return testResponse != null && !testResponse.isEmpty();
        } catch (Exception e) {
            log.warn("Gemini service is not available: {}", e.getMessage());
            return false;
        }
    }
}
