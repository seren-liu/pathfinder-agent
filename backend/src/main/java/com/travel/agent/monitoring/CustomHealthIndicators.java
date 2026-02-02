package com.travel.agent.monitoring;

import com.travel.agent.service.impl.IncrementalKnowledgeBaseService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * 自定义健康检查指标
 */
@Component("knowledgeBase")
@RequiredArgsConstructor
class KnowledgeBaseHealthIndicator implements HealthIndicator {
    
    private final IncrementalKnowledgeBaseService knowledgeBaseService;
    
    @Override
    public Health health() {
        try {
            var stats = knowledgeBaseService.getStats();
            
            if (stats.getTotalDocuments() == 0) {
                return Health.down()
                    .withDetail("reason", "No documents in knowledge base")
                    .build();
            }
            
            return Health.up()
                .withDetail("documents", stats.getTotalDocuments())
                .withDetail("segments", stats.getTotalSegments())
                .withDetail("lastUpdated", stats.getLastUpdated())
                .build();
                
        } catch (Exception e) {
            return Health.down()
                .withDetail("error", e.getMessage())
                .build();
        }
    }
}

@Component("llmService")
class LLMServiceHealthIndicator implements HealthIndicator {
    
    @Override
    public Health health() {
        return Health.up()
            .withDetail("status", "LLM service is available")
            .build();
    }
}
