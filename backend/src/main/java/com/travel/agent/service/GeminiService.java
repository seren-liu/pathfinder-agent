package com.travel.agent.service;

/**
 * Google Gemini AI 服务接口
 */
public interface GeminiService {
    
    /**
     * 通用 AI 对话方法
     * 
     * @param prompt 提示词
     * @return AI 响应内容
     */
    String chat(String prompt);
    
    /**
     * 检查 Gemini 服务是否可用
     * 
     * @return true 如果可用
     */
    boolean isAvailable();
}
