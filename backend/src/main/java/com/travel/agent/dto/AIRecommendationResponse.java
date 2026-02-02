package com.travel.agent.dto;

import lombok.Data;
import java.util.List;

/**
 * AI 推荐响应（用于解析 JSON）
 */
@Data
public class AIRecommendationResponse {
    private List<AIDestinationRecommendation> recommendations;
}
