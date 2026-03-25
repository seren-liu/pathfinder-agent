package com.travel.agent.ai.agent.unified;

import com.travel.agent.config.AgentConfig;
import com.travel.agent.dto.unified.UnifiedTravelIntent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;

/**
 * 会话级 Agent 状态仓库（Redis）
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AgentStateStore {

    private static final String STATE_KEY_PREFIX = "agent:state:";

    private final RedisTemplate<String, Object> redisTemplate;
    private final AgentConfig agentConfig;

    public UnifiedAgentState loadOrCreate(Long userId, String sessionId, String currentMessage) {
        String key = getKey(sessionId);
        Object cached = redisTemplate.opsForValue().get(key);

        UnifiedAgentState state;
        if (cached instanceof UnifiedAgentState cachedState) {
            state = cachedState;
        } else {
            state = UnifiedAgentState.create(userId, sessionId, currentMessage);
        }

        // 每次请求刷新会话上下文
        state.setUserId(userId);
        state.setSessionId(sessionId);
        state.setCurrentMessage(currentMessage);
        state.setLastUpdatedAt(LocalDateTime.now());

        // 兼容历史数据结构
        if (state.getMetadata() == null) {
            state.setMetadata(new HashMap<>());
        }
        if (state.getConversationHistory() == null) {
            state.setConversationHistory(new ArrayList<>());
        }
        if (state.getCurrentPhase() == null) {
            state.setCurrentPhase(UnifiedAgentState.ExecutionPhase.INITIAL);
        }
        if (state.getIterationCount() == null) {
            state.setIterationCount(0);
        }
        if (state.getMaxIterations() == null) {
            state.setMaxIterations(10);
        }
        if (state.getShouldTerminate() == null) {
            state.setShouldTerminate(false);
        }
        if (state.getItineraryStatus() == null) {
            state.setItineraryStatus(UnifiedAgentState.ItineraryStatus.NOT_STARTED);
        }
        if (state.getIntent() == null) {
            state.setIntent(UnifiedTravelIntent.createDefault(userId, sessionId));
        }
        if (state.getRecommendations() == null) {
            state.setRecommendations(new ArrayList<>());
        }
        if (state.getExcludedDestinations() == null) {
            state.setExcludedDestinations(new ArrayList<>());
        }
        if (state.getErrors() == null) {
            state.setErrors(new ArrayList<>());
        }

        return state;
    }

    public void save(UnifiedAgentState state) {
        if (state == null || state.getSessionId() == null) {
            return;
        }

        state.setLastUpdatedAt(LocalDateTime.now());
        redisTemplate.opsForValue().set(
                getKey(state.getSessionId()),
                state,
                agentConfig.getStateCacheTtl()
        );
    }

    public void clear(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return;
        }
        redisTemplate.delete(getKey(sessionId));
    }

    private String getKey(String sessionId) {
        return STATE_KEY_PREFIX + sessionId;
    }
}
