package com.travel.agent.service;

import com.travel.agent.dto.response.DestinationResponse.DestinationPlaceInfo;
import com.travel.agent.dto.response.GeoPlace;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Geoapify Places API 服务
 */
public interface GeoapifyService {
    
    /**
     * 根据目的地名称和坐标获取照片 URL
     * @param destinationName 目的地名称
     * @param latitude 纬度
     * @param longitude 经度
     * @return 照片 URL
     */
    String getDestinationPhoto(String destinationName, Double latitude, Double longitude);
    
    /**
     * 搜索目的地详细信息
     * @param destinationName 目的地名称
     * @param latitude 纬度
     * @param longitude 经度
     * @return 目的地详细信息（评分、分类等）
     */
    DestinationPlaceInfo getPlaceInfo(String destinationName, Double latitude, Double longitude);
    
    /**
     * 地理编码：根据地址查询坐标
     * @param address 地址字符串（如 "Main Beach, Byron Bay, NSW"）
     * @return Map 包含 latitude 和 longitude，如果查询失败返回 null
     */
    Map<String, BigDecimal> geocodeAddress(String address);
    
    /**
     * 搜索附近的地点
     * @param latitude 纬度
     * @param longitude 经度
     * @param radiusKm 搜索半径（公里）
     * @param limit 返回数量限制
     * @param categories 地点类别列表
     * @return 附近地点列表
     */
    List<GeoPlace> searchNearbyPlaces(Double latitude, Double longitude, Double radiusKm, int limit, List<String> categories);

    /**
     * 获取地点的图片URL（内部会优先尝试详情来源，不可用时降级为静态/占位图）
     * @param placeId Geoapify place_id
     * @param placeName 地点名称
     * @param latitude 纬度
     * @param longitude 经度
     * @return 图片URL
     */
    String getPlaceImageUrl(String placeId, String placeName, Double latitude, Double longitude);
}
