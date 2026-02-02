package com.travel.agent.service;

import com.travel.agent.dto.response.TravelSummaryResponse;

public interface TravelSummaryService {
    /**
     * 仅生成summary（分天AI日记）
     */
    TravelSummaryResponse generateSummary(Long tripId);
}


