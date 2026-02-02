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
     * 从 Map 转换
     */
    public static Coordinates fromMap(Map<String, Object> map) {
        if (map == null) return null;
        
        Coordinates coord = new Coordinates();
        coord.setLocation((String) map.get("location"));
        coord.setSuccess((Boolean) map.get("success"));
        coord.setErrorMessage((String) map.get("errorMessage"));
        
        Object lat = map.get("latitude");
        if (lat instanceof BigDecimal) {
            coord.setLatitude((BigDecimal) lat);
        } else if (lat instanceof Number) {
            coord.setLatitude(new BigDecimal(lat.toString()));
        }
        
        Object lon = map.get("longitude");
        if (lon instanceof BigDecimal) {
            coord.setLongitude((BigDecimal) lon);
        } else if (lon instanceof Number) {
            coord.setLongitude(new BigDecimal(lon.toString()));
        }
        
        return coord;
    }
}
