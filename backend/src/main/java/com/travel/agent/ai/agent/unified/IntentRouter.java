package com.travel.agent.ai.agent.unified;

import com.travel.agent.dto.TravelIntent;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 确定性意图路由器（纯 Java 决策）
 */
@Component
public class IntentRouter {

    public static final String TOOL_CONVERSATION = "conversation";
    public static final String TOOL_RECOMMEND = "recommend_destinations";
    public static final String TOOL_GENERATE = "generate_itinerary";
    public static final String TOOL_FINISH = "FINISH";

    public Decision route(AgentState state) {
        if (state == null) {
            return Decision.of(TOOL_CONVERSATION, "state_missing", AgentState.AgentPhase.COLLECTING_INFO);
        }

        if (state.getTripId() != null) {
            return Decision.of(TOOL_FINISH, "trip_already_started", AgentState.AgentPhase.COMPLETED);
        }

        String message = state.getCurrentMessage() == null ? "" : state.getCurrentMessage().trim().toLowerCase(Locale.ROOT);
        TravelIntent intent = state.getIntent();

        // 用户结束语：有上下文时直接完成
        if (isClosingMessage(message) && state.getConversationTurns() != null && state.getConversationTurns() > 0) {
            return Decision.of(TOOL_FINISH, "user_end_of_conversation", AgentState.AgentPhase.COMPLETED);
        }

        if (intent == null) {
            return Decision.of(TOOL_CONVERSATION, "intent_missing", AgentState.AgentPhase.COLLECTING_INFO);
        }

        boolean explicitGenerateRequest = isItineraryGenerationRequest(message);
        boolean implicitGenerateRequest = isImplicitItineraryConfirmation(state, message);
        boolean itineraryGenerationRequested = explicitGenerateRequest || implicitGenerateRequest;
        boolean recommendationEligible = isRecommendationEligible(intent);

        if (Boolean.TRUE.equals(intent.getReadyForItinerary())) {
            if (hasItineraryInputs(intent, state, itineraryGenerationRequested)) {
                String routeReason = explicitGenerateRequest
                        ? "intent_ready_for_itinerary"
                        : (state.getSelectedDestination() != null
                                ? "selected_destination_ready_for_itinerary"
                                : "implicit_itinerary_confirmation");
                return Decision.of(TOOL_GENERATE, routeReason, AgentState.AgentPhase.READY_FOR_ITINERARY);
            }

            // 信息已齐但用户尚未明确要求直接生成时：
            // - 目的地不明确（国家/大洲/模糊）=> 先推荐
            // - 目的地明确（城市/具体地点）=> 先确认是否直接生成行程
            List<String> missingForRecommend = missingRecommendationFields(intent);
            if (!itineraryGenerationRequested && missingForRecommend.isEmpty()) {
                if (recommendationEligible) {
                    return Decision.of(
                            TOOL_RECOMMEND,
                            "itinerary_not_confirmed_recommendation_first",
                            AgentState.AgentPhase.READY_FOR_RECOMMENDATION
                    );
                }
                return Decision.of(
                        TOOL_CONVERSATION,
                        "awaiting_itinerary_confirmation",
                        AgentState.AgentPhase.COLLECTING_INFO
                );
            }
        }

        if (isRefreshRecommendationRequest(message) && state.getRecommendations() != null && !state.getRecommendations().isEmpty()) {
            return Decision.of(TOOL_RECOMMEND, "refresh_recommendation_requested", AgentState.AgentPhase.READY_FOR_RECOMMENDATION);
        }

        if (Boolean.TRUE.equals(intent.getNeedsRecommendation())) {
            if (!recommendationEligible) {
                return Decision.of(
                        TOOL_CONVERSATION,
                        "clear_destination_skip_recommendation",
                        AgentState.AgentPhase.COLLECTING_INFO
                );
            }
            List<String> missing = missingRecommendationFields(intent);
            if (missing.isEmpty()) {
                return Decision.of(TOOL_RECOMMEND, "intent_needs_recommendation", AgentState.AgentPhase.READY_FOR_RECOMMENDATION);
            }
            return Decision.of(
                    TOOL_CONVERSATION,
                    "missing_recommendation_fields:" + String.join(",", missing),
                    AgentState.AgentPhase.COLLECTING_INFO
            );
        }

        return Decision.of(TOOL_CONVERSATION, "need_more_information", AgentState.AgentPhase.COLLECTING_INFO);
    }

    private List<String> missingRecommendationFields(TravelIntent intent) {
        List<String> missing = new ArrayList<>();
        boolean hasPreferenceSignals = (intent.getInterests() != null && !intent.getInterests().isEmpty())
                || isNotBlank(intent.getMood());
        boolean hasDuration = intent.getDays() != null;
        boolean hasBudget = isNotBlank(intent.getBudget());

        if (!hasPreferenceSignals) {
            missing.add("preferences");
        }
        if (!hasDuration) {
            missing.add("days");
        }
        if (!hasBudget) {
            missing.add("budget");
        }
        return missing;
    }

    private boolean hasItineraryInputs(TravelIntent intent, AgentState state, boolean explicitGenerateRequest) {
        boolean hasCoreFields = intent.getDays() != null && isNotBlank(intent.getBudget());
        boolean hasSelectedDestination = state.getSelectedDestination() != null;
        boolean hasExplicitDestinationForGenerate = isNotBlank(intent.getDestination()) && explicitGenerateRequest;
        return hasCoreFields && (hasSelectedDestination || hasExplicitDestinationForGenerate);
    }

    /**
     * 仅当目的地是国家/大洲/模糊层级时才进入推荐流程。
     * 目的地明确（城市/具体地点）时不走推荐，而是收集完信息后确认生成行程。
     */
    private boolean isRecommendationEligible(TravelIntent intent) {
        if (intent == null) {
            return true;
        }
        if (!isNotBlank(intent.getDestination())) {
            return true;
        }
        if (intent.getType() == null) {
            return true;
        }
        return intent.getType() != TravelIntent.IntentType.DESTINATION_CLEAR;
    }

    private boolean isNotBlank(String text) {
        return text != null && !text.isBlank();
    }

    private boolean isClosingMessage(String message) {
        return message.equals("thanks")
                || message.equals("thank you")
                || message.equals("bye")
                || message.equals("goodbye")
                || message.equals("谢谢")
                || message.equals("再见");
    }

    private boolean isRefreshRecommendationRequest(String message) {
        return message.contains("换一批")
                || message.contains("another batch")
                || message.contains("more options")
                || message.contains("different options");
    }

    private boolean isItineraryGenerationRequest(String message) {
        return message.contains("生成行程")
                || message.contains("规划行程")
                || message.contains("安排行程")
                || message.contains("开始生成")
                || message.contains("开始规划")
                || message.contains("直接生成")
                || message.contains("generate itinerary")
                || message.contains("build itinerary")
                || message.contains("plan itinerary")
                || message.contains("start itinerary");
    }

    private boolean isImplicitItineraryConfirmation(AgentState state, String message) {
        if (!isNotBlank(message) || containsAdjustmentSignal(message)) {
            return false;
        }

        if (isAffirmativeStartMessage(message)) {
            return true;
        }

        String previousRouteReason = getPreviousRouteReason(state);
        if (!isNotBlank(previousRouteReason)) {
            return false;
        }

        boolean wasCollectingCoreInfo = previousRouteReason.startsWith("missing_recommendation_fields:")
                || "need_more_information".equals(previousRouteReason)
                || "awaiting_itinerary_confirmation".equals(previousRouteReason)
                || "clear_destination_skip_recommendation".equals(previousRouteReason);

        return wasCollectingCoreInfo && hasSlotFillingSignals(message);
    }

    private boolean isAffirmativeStartMessage(String message) {
        return message.contains("开始吧")
                || message.contains("就这样")
                || message.contains("按这个")
                || message.contains("可以开始")
                || message.contains("直接开始")
                || message.contains("开始规划")
                || message.contains("开始安排行程")
                || message.contains("go ahead")
                || message.contains("start now")
                || message.contains("let's go")
                || message.contains("lets go")
                || message.matches("^(好|好的|行|可以|ok|okay|没问题)[!！。,. ]*$");
    }

    private boolean hasSlotFillingSignals(String message) {
        boolean hasBudgetSignal = message.contains("预算")
                || message.contains("人民币")
                || message.contains("rmb")
                || message.contains("cny")
                || message.matches(".*\\d+[kKwW万]?\\s*(元|块|人民币|rmb|cny|￥).*");
        boolean hasDurationSignal = message.contains("天")
                || message.contains("晚")
                || message.contains("日游")
                || message.matches(".*\\d+\\s*(day|days|night|nights).*");
        boolean hasPreferenceSignal = message.contains("喜欢")
                || message.contains("偏好")
                || message.contains("想要")
                || message.contains("美食")
                || message.contains("亲子")
                || message.contains("情侣")
                || message.contains("citywalk")
                || message.contains("museum")
                || message.contains("food");
        return hasBudgetSignal || hasDurationSignal || hasPreferenceSignal;
    }

    private boolean containsAdjustmentSignal(String message) {
        return message.contains("改")
                || message.contains("调整")
                || message.contains("换成")
                || message.contains("先别")
                || message.contains("先不")
                || message.contains("重新")
                || message.contains("change")
                || message.contains("adjust")
                || message.contains("instead")
                || message.contains("not now");
    }

    private String getPreviousRouteReason(AgentState state) {
        if (state == null || state.getMetadata() == null) {
            return null;
        }
        Object routeReason = state.getMetadata().get("routeReason");
        return routeReason == null ? null : String.valueOf(routeReason);
    }

    public record Decision(String toolName, String reason, AgentState.AgentPhase nextPhase) {
        public static Decision of(String toolName, String reason, AgentState.AgentPhase nextPhase) {
            return new Decision(toolName, reason, nextPhase);
        }
    }
}
