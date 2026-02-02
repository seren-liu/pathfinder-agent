package com.travel.agent.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 文档版本信息
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentVersion {
    private String documentId;
    private String filePath;
    private String contentHash;
    private Integer segmentCount;
    private LocalDateTime lastUpdated;
    private Integer version;
}
