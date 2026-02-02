package com.travel.agent.controller;

import com.travel.agent.dto.request.CreateTripRequest;
import com.travel.agent.dto.request.GenerateItineraryRequest;
import com.travel.agent.dto.response.CommonResponse;
import com.travel.agent.dto.response.TripResponse;
import com.travel.agent.dto.response.TripStatusResponse;
import com.travel.agent.dto.response.TripSummaryResponse;
import com.travel.agent.entity.Trips;
import com.travel.agent.service.ItineraryGenerationService;
import com.travel.agent.service.TripsService;
import java.util.List;
import java.util.stream.Collectors;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.web.bind.annotation.*;

/**
 * <p>
 * Trip plans 前端控制器
 * </p>
 *
 * @author Seren
 * @since 2025-10-19
 */
@Slf4j
@RestController
@RequestMapping("/api/trips")
@RequiredArgsConstructor
public class TripsController {
    
    private final TripsService tripsService;
    private final ItineraryGenerationService itineraryGenerationService;
    
    /**
     * 创建行程
     */
    @PostMapping
    public CommonResponse<Long> createTrip(@Valid @RequestBody CreateTripRequest request) {
        log.debug("Create trip request: userId={}, destinationId={}, days={}", 
            request.getUserId(), request.getDestinationId(), request.getDurationDays());
        
        Long tripId = tripsService.createTrip(request);
        
        return CommonResponse.success(tripId);
    }
    
    /**
     * 获取完整行程
     */
    @GetMapping("/{tripId}")
    public CommonResponse<TripResponse> getTripById(@PathVariable Long tripId) {
        
        
        TripResponse response = tripsService.getTripById(tripId);
        
        return CommonResponse.success(response);
    }
    
    /**
     * 生成行程（异步）
     */
    @PostMapping("/generate")
    public CommonResponse<Long> generateItinerary(@Valid @RequestBody GenerateItineraryRequest request) {
        log.debug("Generate itinerary request: userId={}, destinationId={}, days={}", 
            request.getUserId(), request.getDestinationId(), request.getDurationDays());
        
        // 1. 创建 Trip 记录（状态：generating）
        CreateTripRequest createRequest = new CreateTripRequest();
        createRequest.setUserId(request.getUserId());
        createRequest.setDestinationId(request.getDestinationId());
        createRequest.setStartDate(request.getStartDate());
        createRequest.setEndDate(request.getEndDate());
        createRequest.setDurationDays(request.getDurationDays());
        createRequest.setTotalBudget(request.getTotalBudget());
        createRequest.setPartySize(request.getPartySize());
        
        Long tripId = tripsService.createTrip(createRequest);
        
        // 2. 更新状态为 generating，并保存目的地信息
        Trips trip = tripsService.getById(tripId);
        trip.setStatus("generating");
        
        // ✅ 方案 B: 保存目的地信息到 Trip 表
        trip.setDestinationName(request.getDestinationName());
        trip.setDestinationCountry(request.getDestinationCountry());
        trip.setDestinationLatitude(request.getDestinationLatitude());
        trip.setDestinationLongitude(request.getDestinationLongitude());
        
        tripsService.updateById(trip);
        
        // 3. 异步生成行程
        itineraryGenerationService.generateItineraryAsync(tripId, request);
        
        return CommonResponse.success(tripId);
    }
    
    /**
     * 获取生成状态
     */
    @GetMapping("/{tripId}/status")
    public CommonResponse<TripStatusResponse> getTripStatus(@PathVariable Long tripId) {
        log.debug("Get trip status: tripId={}", tripId);
        
        Trips trip = tripsService.getById(tripId);
        if (trip == null) {
            return CommonResponse.error("Trip not found");
        }
        
        TripStatusResponse response = new TripStatusResponse();
        response.setTripId(tripId);
        response.setStatus(trip.getStatus());
        
        // 从 Redis 获取进度
        Integer progress = itineraryGenerationService.getGenerationProgress(tripId);
        String currentStep = itineraryGenerationService.getCurrentStep(tripId);
        
        response.setProgress(progress);
        response.setCurrentStep(currentStep);
        
        return CommonResponse.success(response);
    }
    
    /**
     * 获取用户最新的行程 ID
     * 使用 Redis 缓存，缓存 5 分钟
     */
    @GetMapping("/users/{userId}/latest")
    @Cacheable(value = "latestTrip", key = "#userId", unless = "#result == null || #result.data == null")
    public CommonResponse<Long> getLatestTrip(@PathVariable Long userId) {
        log.debug("Get latest trip for user: userId={}", userId);
        
        // 查询最新的 completed 行程
        Trips latestTrip = tripsService.lambdaQuery()
                .eq(Trips::getUserId, userId)
                .eq(Trips::getStatus, "completed")
                .orderByDesc(Trips::getCreatedAt)
                .last("LIMIT 1")
                .one();
        
        if (latestTrip != null) {
            log.debug("✅ Found latest trip: tripId={}", latestTrip.getId());
            return CommonResponse.success(latestTrip.getId());
        } else {
            log.debug("⚠️ No completed trips found for user: userId={}", userId);
            return CommonResponse.success(null);
        }
    }
    
    /**
     * 获取用户的所有行程列表
     */
    @GetMapping("/users/{userId}")
    public CommonResponse<List<TripSummaryResponse>> getUserTrips(@PathVariable Long userId) {
        log.debug("Get all trips for user: userId={}", userId);
        
        // 查询用户的所有行程，按创建时间倒序
        List<Trips> trips = tripsService.lambdaQuery()
                .eq(Trips::getUserId, userId)
                .orderByDesc(Trips::getCreatedAt)
                .list();
        
        // 转换为摘要响应
        List<TripSummaryResponse> summaries = trips.stream()
                .map(trip -> {
                    TripSummaryResponse summary = new TripSummaryResponse();
                    summary.setTripId(trip.getId());
                    summary.setDestinationName(trip.getDestinationName());
                    summary.setDestinationCountry(trip.getDestinationCountry());
                    summary.setStartDate(trip.getStartDate());
                    summary.setEndDate(trip.getEndDate());
                    summary.setDurationDays(trip.getDurationDays());
                    summary.setTotalBudget(trip.getTotalBudget());
                    summary.setStatus(trip.getStatus());
                    summary.setCreatedAt(trip.getCreatedAt());
                    return summary;
                })
                .collect(Collectors.toList());
        
        log.debug("✅ Found {} trips for user: userId={}", summaries.size(), userId);
        return CommonResponse.success(summaries);
    }
}
