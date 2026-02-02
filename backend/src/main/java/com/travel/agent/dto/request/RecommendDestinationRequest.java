package com.travel.agent.dto.request;

import com.travel.agent.dto.response.ParseIntentResponse;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
@Schema(description = "目的地推荐请求")
public class RecommendDestinationRequest {

    @NotNull(message = "User ID cannot be null")
    @Schema(description = "用户ID")
    private Long userId;

    @Schema(description = "解析后的意图数据")
    private ParseIntentResponse parsedIntent;
    
    // 兼容前端直接传递的字段
    @Schema(description = "会话ID")
    private String sessionId;
    
    @Schema(description = "目的地偏好（如：南美洲、欧洲、海滩等）")
    private String destination;
    
    @Schema(description = "兴趣列表")
    private List<String> interests;
    
    @Schema(description = "心情")
    private String mood;
    
    @Schema(description = "预算")
    private String budget;
    
    @Schema(description = "天数")
    private Integer days;

    @Schema(description = "排除的目的地ID列表（用于换一批）", example = "[1, 2, 3]")
    private List<Long> excludeIds;

    @Schema(description = "排除的目的地名称列表（用于换一批，AI 生成的推荐使用）", example = "[\"Paris\", \"Tokyo\"]")
    private List<String> excludeNames;

    @Schema(description = "是否强制刷新（跳过缓存，重新调用 AI）", example = "false")
    private Boolean forceRefresh = false;
}
