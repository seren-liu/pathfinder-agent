package com.travel.agent.ai.tools;

import lombok.Builder;
import lombok.Data;

/**
 * 景点信息 DTO
 */
@Data
@Builder
public class AttractionInfo {
    private String name;
    private String category;
    private String price;
    private String description;
    private Double relevanceScore;
    private String city;
}
