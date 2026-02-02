package com.travel.agent.service;

import com.travel.agent.dto.request.CreateTripRequest;
import com.travel.agent.dto.response.TripResponse;
import com.travel.agent.entity.Trips;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 * Trip plans 服务类
 * </p>
 *
 * @author Seren
 * @since 2025-10-19
 */
public interface TripsService extends IService<Trips> {
    
    /**
     * 创建行程
     */
    Long createTrip(CreateTripRequest request);
    
    /**
     * 获取完整行程信息
     */
    TripResponse getTripById(Long tripId);
}
