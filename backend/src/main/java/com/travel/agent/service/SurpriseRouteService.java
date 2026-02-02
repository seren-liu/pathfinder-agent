package com.travel.agent.service;

import com.travel.agent.dto.request.SurpriseRouteRequest;
import com.travel.agent.dto.response.SurpriseRouteResponse;

/**
 * 临时惊喜路线服务接口
 */
public interface SurpriseRouteService {

    /**
     * 生成临时惊喜路线
     * 
     * @param request 请求参数
     * @return 惊喜路线响应
     */
    SurpriseRouteResponse generateSurpriseRoute(SurpriseRouteRequest request);

    /**
     * 重新生成路线（排除已访问的点）
     * 
     * @param request 请求参数
     * @param excludePointIds 要排除的点ID列表
     * @return 新的惊喜路线响应
     */
    SurpriseRouteResponse regenerateSurpriseRoute(SurpriseRouteRequest request, java.util.List<String> excludePointIds);
}
