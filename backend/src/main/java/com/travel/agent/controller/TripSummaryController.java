package com.travel.agent.controller;
import com.travel.agent.dto.response.CommonResponse;
import com.travel.agent.dto.response.TravelSummaryResponse;
import com.travel.agent.service.TravelSummaryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import com.travel.agent.entity.Trips;
import com.travel.agent.service.TripsService;

@Slf4j
@RestController
@RequestMapping("/api/trips")
@RequiredArgsConstructor
public class TripSummaryController {

    private final TravelSummaryService travelSummaryService;
    private final TripsService tripsService;

    /**
     * 生成旅行AI总结（分天分段，忽略图片，只生成Summary文本）
     */
    @PostMapping("/{tripId}/summary")
    public CommonResponse<TravelSummaryResponse> generateSummary(
            @PathVariable Long tripId) {
        TravelSummaryResponse resp = travelSummaryService.generateSummary(tripId);
        return CommonResponse.success(resp);
    }

    /**
     * 获取当前AI summary（只查DB，不调AI，不更新）
     */
    @GetMapping("/{tripId}/summary")
    public CommonResponse<TravelSummaryResponse> getSummaryOnly(
            @PathVariable Long tripId) {
        Trips trip = tripsService.getById(tripId);
        if(trip == null){
            return CommonResponse.error("Trip not found");
        }
        TravelSummaryResponse resp = new TravelSummaryResponse();
        resp.setTripId(tripId);
        resp.setTextSummary(trip.getAiSummary());
        resp.setPhotoAnalyses(null);
        resp.setShareTitle("My trip to " + (trip.getDestinationName()==null?"":trip.getDestinationName()));
        resp.setShareText(trip.getAiSummary());
        return CommonResponse.success(resp);
    }
}


