package com.travel.agent.service;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Mapbox Geocoding 服务接口
 */
public interface MapboxGeocodingService {
    
    /**
     * 地理编码：将地址转换为经纬度坐标
     * 
     * @param address 地址字符串（如 "Main Beach, Byron Bay, NSW, Australia"）
     * @return Map 包含 latitude 和 longitude，如果查询失败返回 null
     */
    Map<String, BigDecimal> geocodeAddress(String address);
    
    /**
     * 批量地理编码（带缓存优化）
     * 
     * @param addresses 地址列表
     * @return Map<地址, 坐标>
     */
    Map<String, Map<String, BigDecimal>> batchGeocode(java.util.List<String> addresses);
}
