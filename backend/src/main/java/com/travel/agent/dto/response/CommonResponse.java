package com.travel.agent.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * 统一响应格式
 * 
 * @param <T> 响应数据类型
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CommonResponse<T> {
    
    private Integer code;
    private String message;
    private T data;
    private Long timestamp;

    /**
     * 成功响应（带数据）
     */
    public static <T> CommonResponse<T> success(T data) {
        return new CommonResponse<>(200, "Success", data, Instant.now().toEpochMilli());
    }

    /**
     * 成功响应（无数据）
     */
    public static <T> CommonResponse<T> success() {
        return new CommonResponse<>(200, "Success", null, Instant.now().toEpochMilli());
    }

    /**
     * 成功响应（自定义消息）
     */
    public static <T> CommonResponse<T> success(String message, T data) {
        return new CommonResponse<>(200, message, data, Instant.now().toEpochMilli());
    }

    /**
     * 失败响应
     */
    public static <T> CommonResponse<T> error(Integer code, String message) {
        return new CommonResponse<>(code, message, null, Instant.now().toEpochMilli());
    }

    /**
     * 失败响应（默认 500 错误码）
     */
    public static <T> CommonResponse<T> error(String message) {
        return new CommonResponse<>(500, message, null, Instant.now().toEpochMilli());
    }
}

