package com.travel.agent.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

@Data
@Schema(description = "Travel Summary Response")
public class TravelSummaryResponse {

    @Schema(description = "Trip ID")
    private Long tripId;

    @Schema(description = "AI generated text summary")
    private String textSummary;

    @Schema(description = "AI generated photo descriptions")
    private List<PhotoAnalysis> photoAnalyses;

    @Schema(description = "Suggested share title")
    private String shareTitle;

    @Schema(description = "Suggested share text")
    private String shareText;

    @Data
    public static class PhotoAnalysis {
        @Schema(description = "Photo URL")
        private String url;

        @Schema(description = "AI generated concise English description for the photo")
        private String description;
    }
}


