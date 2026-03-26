package com.travel.agent.ai.agent.unified;

import com.travel.agent.dto.unified.UnifiedTravelIntent;
import org.springframework.stereotype.Component;

import java.util.Locale;

/**
 * 确定性意图路由器 —— 纯数据驱动决策，不依赖消息关键词。
 *
 * <p>路由优先级（从高到低）：
 * <ol>
 *   <li>终止条件：shouldTerminate / tripId 已创建 / 告别语</li>
 *   <li>"换一批"：用户主动刷新推荐</li>
 *   <li>已选定目的地 + 天数 + 预算 → 生成行程</li>
 *   <li>城市级目的地 + 天数 + 预算 → 生成行程</li>
 *   <li>模糊/国家/区域目的地 + 天数 + 预算 → 推荐具体目的地</li>
 *   <li>缺少目的地 / 天数 / 预算 → 对话收集</li>
 * </ol>
 *
 * <p>设计原则：一旦三要素（目的地方向 + 天数 + 预算）收集完成，立即自动跳转，
 * 无需等待用户说"开始"等特定关键词。消息内容仅用于检测"换一批"等显式覆盖指令。
 */
@Component
public class IntentRouter {

    public static final String TOOL_CONVERSATION = "conversation";
    public static final String TOOL_RECOMMEND    = "recommend_destinations";
    public static final String TOOL_GENERATE     = "generate_itinerary";
    public static final String TOOL_FINISH       = "FINISH";

    public Decision route(UnifiedAgentState state) {
        // ── 1. 基础防御 ──────────────────────────────────────────────
        if (state == null) {
            return Decision.of(TOOL_CONVERSATION, "state_missing", UnifiedAgentState.ExecutionPhase.CONVERSING);
        }
        if (Boolean.TRUE.equals(state.getShouldTerminate())) {
            return Decision.of(TOOL_FINISH,
                    "terminated:" + nullSafe(state.getTerminationReason()),
                    UnifiedAgentState.ExecutionPhase.COMPLETED);
        }
        if (state.getTripId() != null) {
            return Decision.of(TOOL_FINISH, "trip_already_started", UnifiedAgentState.ExecutionPhase.COMPLETED);
        }

        String message = state.getCurrentMessage() == null
                ? ""
                : state.getCurrentMessage().trim().toLowerCase(Locale.ROOT);
        UnifiedTravelIntent intent = state.getIntent();

        // ── 2. 告别检测 ───────────────────────────────────────────────
        if (isClosingMessage(message) && hasConversationHistory(state)) {
            return Decision.of(TOOL_FINISH, "user_end_of_conversation", UnifiedAgentState.ExecutionPhase.COMPLETED);
        }

        if (intent == null) {
            return Decision.of(TOOL_CONVERSATION, "intent_missing", UnifiedAgentState.ExecutionPhase.CONVERSING);
        }

        // ── 3. "换一批"：刷新推荐（唯一保留的消息关键词覆盖）────────────
        if (isRefreshRecommendationRequest(message)
                && state.getRecommendations() != null
                && !state.getRecommendations().isEmpty()) {
            return Decision.of(TOOL_RECOMMEND, "refresh_recommendation_requested",
                    UnifiedAgentState.ExecutionPhase.RECOMMENDING);
        }

        // ── 4. 纯数据驱动路由 ─────────────────────────────────────────
        boolean hasDestination      = isNotBlank(intent.getDestination());
        boolean hasDays             = intent.getDays() != null;
        boolean hasBudget           = intent.getBudget() != null;
        boolean hasSelectedDest     = state.getSelectedDestination() != null;
        boolean isCityOrSpecific    = isCityLevelOrSpecific(intent);

        // 4a. 用户已从推荐中选定具体目的地 → 可以生成行程
        if (hasSelectedDest && hasDays && hasBudget) {
            return Decision.of(TOOL_GENERATE, "selected_destination_with_full_info",
                    UnifiedAgentState.ExecutionPhase.GENERATING_ITINERARY);
        }

        // 4b. 目的地是明确城市 + 天数 + 预算 → 直接生成行程
        if (isCityOrSpecific && hasDestination && hasDays && hasBudget) {
            return Decision.of(TOOL_GENERATE, "city_destination_ready_to_generate",
                    UnifiedAgentState.ExecutionPhase.GENERATING_ITINERARY);
        }

        // 4c. 目的地是国家/区域/模糊方向 + 天数 + 预算 → 推荐具体目的地
        if (hasDestination && !isCityOrSpecific && hasDays && hasBudget) {
            return Decision.of(TOOL_RECOMMEND, "vague_destination_needs_recommendation",
                    UnifiedAgentState.ExecutionPhase.RECOMMENDING);
        }

        // 4d. 无明确目的地，但有兴趣/心情 + 天数 + 预算 → 推荐（基于偏好选目的地）
        boolean hasPreferences = (intent.getInterests() != null && !intent.getInterests().isEmpty())
                || intent.getMood() != null;
        if (!hasDestination && hasPreferences && hasDays && hasBudget) {
            return Decision.of(TOOL_RECOMMEND, "no_destination_preference_based_recommend",
                    UnifiedAgentState.ExecutionPhase.RECOMMENDING);
        }

        // 4e. 信息不足 → 对话收集缺失字段
        return Decision.of(TOOL_CONVERSATION,
                buildMissingReason(hasDestination, hasDays, hasBudget),
                UnifiedAgentState.ExecutionPhase.CONVERSING);
    }

    // ── 辅助：目的地级别判断 ─────────────────────────────────────────────

    /**
     * 判断目的地是否足够具体（城市级），可以直接生成行程而无需先推荐。
     *
     * <p>判断优先级：
     * <ol>
     *   <li>destinationType == CITY → 明确城市，无需推荐</li>
     *   <li>destinationType == COUNTRY / REGION / VAGUE → 模糊，需要推荐</li>
     *   <li>destinationType == UNKNOWN / null → 用 readyForItinerary 规则兜底
     *       （该 flag 由 StructuredIntentExtractor 的规则路径基于完整 ISO token 集合推断）</li>
     * </ol>
     */
    private boolean isCityLevelOrSpecific(UnifiedTravelIntent intent) {
        if (intent == null) return false;
        UnifiedTravelIntent.DestinationType type = intent.getDestinationType();
        if (type == UnifiedTravelIntent.DestinationType.CITY) {
            return true;
        }
        if (type == UnifiedTravelIntent.DestinationType.COUNTRY
                || type == UnifiedTravelIntent.DestinationType.REGION
                || type == UnifiedTravelIntent.DestinationType.VAGUE) {
            return false;
        }
        // UNKNOWN 或 null：回退到规则路径设置的 readyForItinerary 标志
        return Boolean.TRUE.equals(intent.getReadyForItinerary());
    }

    /**
     * 构造缺少信息的路由原因字符串，便于日志追踪。
     */
    private String buildMissingReason(boolean hasDestination, boolean hasDays, boolean hasBudget) {
        if (!hasDestination && !hasDays && !hasBudget) return "missing:destination,days,budget";
        if (!hasDestination && !hasDays)  return "missing:destination,days";
        if (!hasDestination && !hasBudget) return "missing:destination,budget";
        if (!hasDays && !hasBudget)        return "missing:days,budget";
        if (!hasDestination) return "missing:destination";
        if (!hasDays)        return "missing:days";
        if (!hasBudget)      return "missing:budget";
        return "need_more_information";
    }

    // ── 辅助：消息检测（仅保留必要的两个）─────────────────────────────────

    /**
     * 检测用户是否在结束对话（精确匹配，避免误触发）。
     */
    private boolean isClosingMessage(String message) {
        return message.equals("thanks")
                || message.equals("thank you")
                || message.equals("bye")
                || message.equals("goodbye")
                || message.equals("谢谢")
                || message.equals("再见");
    }

    /**
     * 检测用户是否要求刷新推荐列表（"换一批"等）。
     */
    private boolean isRefreshRecommendationRequest(String message) {
        return message.contains("换一批")
                || message.contains("another batch")
                || message.contains("more options")
                || message.contains("different options");
    }

    // ── 通用工具方法 ───────────────────────────────────────────────────────

    private boolean isNotBlank(String text) {
        return text != null && !text.isBlank();
    }

    private boolean hasConversationHistory(UnifiedAgentState state) {
        return state != null
                && state.getConversationHistory() != null
                && !state.getConversationHistory().isEmpty();
    }

    private String nullSafe(String value) {
        return value == null ? "unknown" : value;
    }

    // ── 路由决策结果 ───────────────────────────────────────────────────────

    public record Decision(String toolName, String reason, UnifiedAgentState.ExecutionPhase nextPhase) {
        public static Decision of(String toolName, String reason, UnifiedAgentState.ExecutionPhase nextPhase) {
            return new Decision(toolName, reason, nextPhase);
        }
    }
}
