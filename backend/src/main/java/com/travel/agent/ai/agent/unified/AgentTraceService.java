package com.travel.agent.ai.agent.unified;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;

/**
 * Agent tracing 封装
 */
@Service
@RequiredArgsConstructor
public class AgentTraceService {

    private final ObservationRegistry observationRegistry;

    public String ensureTraceId(AgentState state) {
        Map<String, Object> metadata = state.getMetadata();
        Object existing = metadata.get("traceId");
        if (existing instanceof String s && !s.isBlank()) {
            return s;
        }
        String traceId = UUID.randomUUID().toString();
        metadata.put("traceId", traceId);
        return traceId;
    }

    public Observation startExecutionTrace(String sessionId, String traceId) {
        return Observation.start("agent.unified.execution", observationRegistry)
                .highCardinalityKeyValue("session.id", safe(sessionId))
                .highCardinalityKeyValue("trace.id", safe(traceId));
    }

    public Observation startToolTrace(String sessionId, String traceId, String toolName) {
        return Observation.start("agent.unified.tool.execution", observationRegistry)
                .highCardinalityKeyValue("session.id", safe(sessionId))
                .highCardinalityKeyValue("trace.id", safe(traceId))
                .lowCardinalityKeyValue("tool", safe(toolName));
    }

    private String safe(String value) {
        return value == null || value.isBlank() ? "unknown" : value;
    }
}
