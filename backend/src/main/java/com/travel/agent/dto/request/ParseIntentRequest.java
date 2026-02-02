package com.travel.agent.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(description = "意图解析请求")
public class ParseIntentRequest {

    @NotNull(message = "User ID cannot be null")
    @Schema(description = "用户ID", example = "1001")
    private Long userId;

    @NotBlank(message = "User input cannot be empty")
    @Schema(description = "用户输入的自然语言", example = "I need to relax after a stressful semester...")
    private String userInput;
}
