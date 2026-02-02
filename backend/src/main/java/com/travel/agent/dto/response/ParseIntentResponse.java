package com.travel.agent.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "意图解析响应")
public class ParseIntentResponse {

    @Schema(description = "用户情绪", example = "relaxed")
    private String mood;
    
    @Schema(description = "目的地偏好", example = "South America")
    private String destination;

    @Schema(description = "关键词列表", example = "[\"beach\", \"mountains\", \"relax\"]")
    private List<String> keywords;

    @Schema(description = "预算等级 (1=低, 2=中, 3=高)", example = "2")
    private Integer budgetLevel;

    @Schema(description = "偏好特征", example = "[\"beach\", \"nature\", \"mountains\"]")
    private List<String> preferredFeatures;

    @Schema(description = "预计旅行天数", example = "5")
    private Integer estimatedDuration;

    @Schema(description = "AI生成的后续问题", example = "[\"Are you looking for domestic or international?\"]")
    private List<String> followUpQuestions;

    @Schema(description = "会话ID", example = "550e8400-e29b-41d4-a716-446655440000")
    private String sessionId;
}
