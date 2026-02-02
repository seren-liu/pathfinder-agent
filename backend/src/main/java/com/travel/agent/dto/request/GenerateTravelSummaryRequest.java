package com.travel.agent.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

@Data
@Schema(description = "生成旅行总结请求")
public class GenerateTravelSummaryRequest {

    @Schema(description = "照片 URL 列表（可选，用于简单情绪分析）")
    private List<String> photoUrls;
}


