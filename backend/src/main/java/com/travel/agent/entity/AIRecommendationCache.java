package com.travel.agent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

import com.travel.agent.dto.AIDestinationRecommendation;

/**
 * AI 推荐缓存表
 * 用于缓存 AI 生成的目的地推荐，避免重复调用 API
 *
 * @author Seren
 * @since 2025-10-18
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName(value = "ai_recommendations", autoResultMap = true)
@Schema(name = "AIRecommendationCache", description = "AI 推荐缓存")
public class AIRecommendationCache implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    @Schema(description = "主键 ID")
    private Long id;

    @Schema(description = "用户 ID")
    private Long userId;

    @Schema(description = "会话 ID")
    private String sessionId;

    @Schema(description = "意图哈希（用于缓存键）")
    private String intentHash;

    @Schema(description = "心情")
    private String mood;

    @Schema(description = "关键词（JSON 数组）")
    private String keywords;

    @Schema(description = "偏好特征（JSON 数组）")
    private String preferredFeatures;

    @Schema(description = "预算等级 (1=Budget, 2=Moderate, 3=Luxury)")
    private Byte budgetLevel;

    @Schema(description = "预计天数")
    private Integer estimatedDuration;

    @TableField(typeHandler = JacksonTypeHandler.class)
    @Schema(description = "推荐结果（JSON 格式，包含 3 个目的地）")
    private List<AIDestinationRecommendation> recommendations;

    @Schema(description = "创建时间")
    private LocalDateTime createdAt;

    @Schema(description = "Redis 过期时间")
    private LocalDateTime expiresAt;
}
