package com.travel.agent.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 知识库统计信息
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeBaseStats {
    private Integer totalDocuments;
    private Integer totalSegments;
    private LocalDateTime lastUpdated;
    private List<DocumentVersion> documents;
}
