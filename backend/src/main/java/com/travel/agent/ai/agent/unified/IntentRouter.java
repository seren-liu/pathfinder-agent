package com.travel.agent.ai.agent.unified;

import com.travel.agent.dto.unified.UnifiedTravelIntent;
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

    public Decision route(UnifiedAgentState state) {
        if (state == null) {
            return Decision.of(TOOL_CONVERSATION, "state_missing", UnifiedAgentState.ExecutionPhase.CONVERSING);
        }

        if (Boolean.TRUE.equals(state.getShouldTerminate())) {
            return Decision.of(TOOL_FINISH, "terminated:" + nullSafe(state.getTerminationReason()), UnifiedAgentState.ExecutionPhase.COMPLETED);
        }

        if (state.getTripId() != null) {
            return Decision.of(TOOL_FINISH, "trip_already_started", UnifiedAgentState.ExecutionPhase.COMPLETED);
        }

        String message = state.getCurrentMessage() == null ? "" : state.getCurrentMessage().trim().toLowerCase(Locale.ROOT);
        UnifiedTravelIntent intent = state.getIntent();

        if (isClosingMessage(message) && hasConversationHistory(state)) {
            return Decision.of(TOOL_FINISH, "user_end_of_conversation", UnifiedAgentState.ExecutionPhase.COMPLETED);
        }

        if (intent == null) {
            return Decision.of(TOOL_CONVERSATION, "intent_missing", UnifiedAgentState.ExecutionPhase.CONVERSING);
        }

        boolean explicitGenerateRequest = isItineraryGenerationRequest(message);
        boolean implicitGenerateRequest = isImplicitItineraryConfirmation(message);
        boolean itineraryGenerationRequested = explicitGenerateRequest || implicitGenerateRequest;
        boolean recommendationEligible = isRecommendationEligible(intent);

        // 用户明确要求开始生成时，只要核心字段齐全且目的地足够具体（城市/国家/已选择推荐），直接生成。
        if (itineraryGenerationRequested && canForceGenerate(intent, state)) {
            return Decision.of(
                    TOOL_GENERATE,
                    "explicit_generate_request_with_sufficient_inputs",
                    UnifiedAgentState.ExecutionPhase.GENERATING_ITINERARY
            );
        }

        if (Boolean.TRUE.equals(intent.getReadyForItinerary())) {
            if (hasItineraryInputs(intent, state, itineraryGenerationRequested)) {
                String routeReason = explicitGenerateRequest
                        ? "intent_ready_for_itinerary"
                        : (state.getSelectedDestination() != null
                        ? "selected_destination_ready_for_itinerary"
                        : "implicit_itinerary_confirmation");
                return Decision.of(TOOL_GENERATE, routeReason, UnifiedAgentState.ExecutionPhase.GENERATING_ITINERARY);
            }

            if (!itineraryGenerationRequested) {
                if (!recommendationEligible) {
                    return Decision.of(
                            TOOL_CONVERSATION,
                            "awaiting_itinerary_confirmation",
                            UnifiedAgentState.ExecutionPhase.CONVERSING
                    );
                }

                List<String> missingForRecommend = missingRecommendationFields(state, intent);
                if (missingForRecommend.isEmpty()) {
                    return Decision.of(
                            TOOL_RECOMMEND,
                            "itinerary_not_confirmed_recommendation_first",
                            UnifiedAgentState.ExecutionPhase.RECOMMENDING
                    );
                }
                return Decision.of(
                        TOOL_CONVERSATION,
                        "missing_recommendation_fields:" + String.join(",", missingForRecommend),
                        UnifiedAgentState.ExecutionPhase.CONVERSING
                );
            }
        }

        if (isRefreshRecommendationRequest(message)
                && state.getRecommendations() != null
                && !state.getRecommendations().isEmpty()) {
            return Decision.of(TOOL_RECOMMEND, "refresh_recommendation_requested", UnifiedAgentState.ExecutionPhase.RECOMMENDING);
        }

        if (Boolean.TRUE.equals(intent.getNeedsRecommendation())) {
            if (!recommendationEligible) {
                return Decision.of(
                        TOOL_CONVERSATION,
                        "clear_destination_skip_recommendation",
                        UnifiedAgentState.ExecutionPhase.CONVERSING
                );
            }
            List<String> missing = missingRecommendationFields(state, intent);
            if (missing.isEmpty()) {
                return Decision.of(TOOL_RECOMMEND, "intent_needs_recommendation", UnifiedAgentState.ExecutionPhase.RECOMMENDING);
            }
            return Decision.of(
                    TOOL_CONVERSATION,
                    "missing_recommendation_fields:" + String.join(",", missing),
                    UnifiedAgentState.ExecutionPhase.CONVERSING
            );
        }

        return Decision.of(TOOL_CONVERSATION, "need_more_information", UnifiedAgentState.ExecutionPhase.CONVERSING);
    }

    private List<String> missingRecommendationFields(UnifiedAgentState state, UnifiedTravelIntent intent) {
        List<String> missing = new ArrayList<>();
        boolean hasDestination = isNotBlank(intent.getDestination());
        boolean hasPreferenceSignals = (intent.getInterests() != null && !intent.getInterests().isEmpty())
                || isNotBlank(intent.getMood());
        boolean hasDuration = intent.getDays() != null;
        boolean hasBudget = intent.getBudget() != null;

        boolean allowSkipPreferences = shouldAllowRecommendationWithoutPreferences(
                state, hasDestination, hasDuration, hasBudget
        );

        if (!hasPreferenceSignals && !allowSkipPreferences) {
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

    private boolean shouldAllowRecommendationWithoutPreferences(
            UnifiedAgentState state,
            boolean hasDestination,
            boolean hasDuration,
            boolean hasBudget
    ) {
        if (!hasDestination || !hasDuration || !hasBudget) {
            return false;
        }
        return hasConversationHistory(state);
    }

    private boolean hasItineraryInputs(UnifiedTravelIntent intent, UnifiedAgentState state, boolean explicitGenerateRequest) {
        boolean hasCoreFields = intent.getDays() != null && intent.getBudget() != null;
        boolean hasSelectedDestination = state.getSelectedDestination() != null;
        boolean hasExplicitDestinationForGenerate = isNotBlank(intent.getDestination()) && explicitGenerateRequest;
        return hasCoreFields && (hasSelectedDestination || hasExplicitDestinationForGenerate);
    }

    private boolean canForceGenerate(UnifiedTravelIntent intent, UnifiedAgentState state) {
        if (intent == null) {
            return false;
        }
        boolean hasCoreFields = intent.getDays() != null && intent.getBudget() != null;
        if (!hasCoreFields) {
            return false;
        }
        if (state != null && state.getSelectedDestination() != null) {
            return true;
        }
        if (!isNotBlank(intent.getDestination())) {
            return false;
        }
        UnifiedTravelIntent.DestinationType destinationType = intent.getDestinationType();
        if (destinationType == null || destinationType == UnifiedTravelIntent.DestinationType.UNKNOWN) {
            return true;
        }
        return destinationType == UnifiedTravelIntent.DestinationType.CITY
                || destinationType == UnifiedTravelIntent.DestinationType.COUNTRY;
    }

    /**
     * 仅当目的地是国家/区域/模糊层级时进入推荐流程。
     */
    private boolean isRecommendationEligible(UnifiedTravelIntent intent) {
        if (intent == null) {
            return true;
        }
        if (!isNotBlank(intent.getDestination())) {
            return true;
        }

        UnifiedTravelIntent.DestinationType destinationType = intent.getDestinationType();
        if (destinationType == null || destinationType == UnifiedTravelIntent.DestinationType.UNKNOWN) {
            return true;
        }
        return destinationType != UnifiedTravelIntent.DestinationType.CITY;
    }

    private boolean isNotBlank(String text) {
        return text != null && !text.isBlank();
    }

    private boolean hasConversationHistory(UnifiedAgentState state) {
        return state != null
                && state.getConversationHistory() != null
                && !state.getConversationHistory().isEmpty();
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
                || message.contains("开始吧")
                || message.contains("开始")
                || message.contains("可以开始")
                || message.contains("确认开始")
                || message.contains("就这样")
                || message.contains("直接生成")
                || message.contains("generate itinerary")
                || message.contains("build itinerary")
                || message.contains("plan itinerary")
                || message.contains("start itinerary")
                || message.contains("start now")
                || message.contains("go ahead");
    }

    private boolean isImplicitItineraryConfirmation(String message) {
        if (!isNotBlank(message) || containsAdjustmentSignal(message)) {
            return false;
        }
        return isAffirmativeStartMessage(message);
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

    private String nullSafe(String value) {
        return value == null ? "unknown" : value;
    }

    public record Decision(String toolName, String reason, UnifiedAgentState.ExecutionPhase nextPhase) {
        public static Decision of(String toolName, String reason, UnifiedAgentState.ExecutionPhase nextPhase) {
            return new Decision(toolName, reason, nextPhase);
        }
    }
}
