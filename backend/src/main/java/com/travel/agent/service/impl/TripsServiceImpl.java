package com.travel.agent.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.travel.agent.dto.request.CreateTripRequest;
import com.travel.agent.dto.response.ItineraryDayResponse;
import com.travel.agent.dto.response.ItineraryItemResponse;
import com.travel.agent.dto.response.TripResponse;
import com.travel.agent.entity.*;
import com.travel.agent.exception.BusinessException;
import com.travel.agent.mapper.TripsMapper;
import com.travel.agent.service.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * <p>
 * Trip plans 服务实现类
 * </p>
 *
 * @author Seren
 * @since 2025-10-19
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TripsServiceImpl extends ServiceImpl<TripsMapper, Trips> implements TripsService {
    
    private final ItineraryDaysService itineraryDaysService;
    private final ItineraryItemsService itineraryItemsService;
    private final DestinationsService destinationsService;
    
    /**
     * 创建行程（暂时创建空行程，后续由 AI 生成内容）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createTrip(CreateTripRequest request) {
        log.info("Creating trip for user: {}, destination: {}", request.getUserId(), request.getDestinationId());
        
        // 1. 创建 Trip 记录
        Trips trip = new Trips();
        trip.setUserId(request.getUserId());
        trip.setDestinationId(request.getDestinationId());
        trip.setStartDate(request.getStartDate());
        trip.setEndDate(request.getEndDate());
        trip.setDurationDays(request.getDurationDays());
        trip.setTotalBudget(request.getTotalBudget());
        trip.setPartySize(request.getPartySize());
        trip.setCurrency("AUD");
        trip.setStatus("planning");
        trip.setCurrentVersion(1);
        trip.setCreatedAt(LocalDateTime.now());
        trip.setUpdatedAt(LocalDateTime.now());
        
        boolean saved = this.save(trip);
        if (!saved) {
            throw new BusinessException("Failed to create trip");
        }
        
        log.info("✅ Trip created successfully: tripId={}", trip.getId());
        return trip.getId();
    }
    
    /**
     * 获取完整行程信息
     * 使用 Redis 缓存，缓存 key 为 "trip::tripId"
     * 缓存时间：10 分钟（在 CacheConfig 中配置）
     */
    @Override
    @Cacheable(value = "trip", key = "#tripId", unless = "#result == null")
    public TripResponse getTripById(Long tripId) {
        log.info("Fetching trip from database: tripId={}", tripId);
        
        // 1. 获取 Trip 基本信息
        Trips trip = this.getById(tripId);
        if (trip == null) {
            throw new BusinessException("Trip not found: " + tripId);
        }
        
        // 2. 获取目的地信息（方案 B：优先使用 Trip 中的目的地信息）
        String destinationName;
        String destinationCountry;
        
        if (trip.getDestinationId() != null) {
            // 如果有 destinationId，从数据库获取完整信息
            Destinations destination = destinationsService.getById(trip.getDestinationId());
            if (destination != null) {
                destinationName = destination.getName();
                destinationCountry = destination.getCountry();
            } else {
                // 兜底：使用 Trip 中保存的信息
                destinationName = trip.getDestinationName();
                destinationCountry = trip.getDestinationCountry();
            }
        } else {
            // 无 destinationId：直接使用 Trip 中保存的信息
            destinationName = trip.getDestinationName();
            destinationCountry = trip.getDestinationCountry();
        }
        
        // 3. 获取所有天数
        List<ItineraryDays> days = itineraryDaysService.list(
            new LambdaQueryWrapper<ItineraryDays>()
                .eq(ItineraryDays::getTripId, tripId)
                .orderByAsc(ItineraryDays::getDayNumber)
        );
        
        // 4. 获取所有活动
        List<ItineraryItems> allItems = itineraryItemsService.list(
            new LambdaQueryWrapper<ItineraryItems>()
                .eq(ItineraryItems::getTripId, tripId)
                .orderByAsc(ItineraryItems::getOrderIndex)
        );
        
        // 5. 组装响应
        TripResponse response = new TripResponse();
        response.setTripId(trip.getId());
        response.setUserId(trip.getUserId());
        response.setDestinationId(trip.getDestinationId());
        response.setDestinationName(destinationName);  // ✅ 使用变量
        response.setDestinationCountry(destinationCountry);  // ✅ 使用变量
        response.setStartDate(trip.getStartDate());
        response.setEndDate(trip.getEndDate());
        response.setDurationDays(trip.getDurationDays());
        response.setTotalBudget(trip.getTotalBudget());
        response.setStatus(trip.getStatus());
        
        // 6. 组装每日行程
        List<ItineraryDayResponse> dayResponses = new ArrayList<>();
        BigDecimal actualTotalCost = BigDecimal.ZERO;
        
        for (ItineraryDays day : days) {
            ItineraryDayResponse dayResponse = new ItineraryDayResponse();
            dayResponse.setDayId(day.getId());  // ✅ 添加dayId
            dayResponse.setDayNumber(day.getDayNumber());
            dayResponse.setDate(day.getDate());
            dayResponse.setTheme(day.getTheme());
            
            // 获取当天的活动
            List<ItineraryItemResponse> itemResponses = allItems.stream()
                .filter(item -> item.getDayId().equals(day.getId()))
                .map(item -> {
                    ItineraryItemResponse itemResponse = convertToItemResponse(item);
                    itemResponse.setDayNumber(day.getDayNumber());
                    return itemResponse;
                })
                .collect(Collectors.toList());
            
            dayResponse.setActivities(itemResponses);
            dayResponse.setActivityCount(itemResponses.size());
            
            // 计算当天总费用和时长
            BigDecimal dayCost = itemResponses.stream()
                .map(ItineraryItemResponse::getCost)
                .filter(cost -> cost != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
            
            Integer dayDuration = itemResponses.stream()
                .map(ItineraryItemResponse::getDurationMinutes)
                .filter(duration -> duration != null)
                .reduce(0, Integer::sum);
            
            dayResponse.setTotalCost(dayCost);
            dayResponse.setTotalDuration(dayDuration);
            
            actualTotalCost = actualTotalCost.add(dayCost);
            dayResponses.add(dayResponse);
        }
        
        response.setDays(dayResponses);
        response.setActualTotalCost(actualTotalCost);
        
        // 7. 设置目的地中心坐标（使用 Trip 中保存的坐标）
        TripResponse.CoordinateResponse center = new TripResponse.CoordinateResponse();
        center.setLatitude(trip.getDestinationLatitude());  // ✅ 使用 Trip 中的坐标
        center.setLongitude(trip.getDestinationLongitude());  // ✅ 使用 Trip 中的坐标
        response.setDestinationCenter(center);
        
        log.info("✅ Trip fetched successfully: tripId={}, days={}, totalCost={}", 
            tripId, dayResponses.size(), actualTotalCost);
        
        return response;
    }
    
    /**
     * 转换 ItineraryItems 到 ItineraryItemResponse
     */
    private ItineraryItemResponse convertToItemResponse(ItineraryItems item) {
        ItineraryItemResponse response = new ItineraryItemResponse();
        BeanUtils.copyProperties(item, response);
        response.setActivityId(item.getId());
        
        // 调试日志：检查坐标是否正确复制
        log.debug("Converting item: {} -> lat={}, lon={}", 
            item.getActivityName(), item.getLatitude(), item.getLongitude());
        
        return response;
    }
}
