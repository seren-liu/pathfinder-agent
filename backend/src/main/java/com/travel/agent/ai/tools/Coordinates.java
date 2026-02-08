package com.travel.agent.ai.tools;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Map;

/**
 * 坐标信息 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Coordinates {
    private BigDecimal latitude;
    private BigDecimal longitude;
    private String location;
    private Boolean success;
    private String errorMessage;
    
    /**
     * 从 Map 转换（使用 Builder 模式）
     */
    public static Coordinates fromMap(Map<String, Object> map) {
        if (map == null) return null;

        CoordinatesBuilder builder = Coordinates.builder()
                .location((String) map.get("location"))
                .success((Boolean) map.get("success"))
                .errorMessage((String) map.get("errorMessage"));

        Object lat = map.get("latitude");
        if (lat instanceof BigDecimal) {
            builder.latitude((BigDecimal) lat);
        } else if (lat instanceof Number) {
            builder.latitude(new BigDecimal(lat.toString()));
        }

        Object lon = map.get("longitude");
        if (lon instanceof BigDecimal) {
            builder.longitude((BigDecimal) lon);
        } else if (lon instanceof Number) {
            builder.longitude(new BigDecimal(lon.toString()));
        }

        return builder.build();
    }
}
