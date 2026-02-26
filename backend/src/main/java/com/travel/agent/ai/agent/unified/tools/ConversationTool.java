package com.travel.agent.ai.agent.unified.tools;

import com.travel.agent.ai.agent.ActionResult;
import com.travel.agent.ai.agent.unified.AgentState;
import com.travel.agent.ai.agent.unified.UnifiedAgentTool;
import com.travel.agent.dto.TravelIntent;
import com.travel.agent.dto.response.ChatResponse;
import com.travel.agent.service.ConversationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 对话工具 - 包装现有 ConversationService
 * 
 * 不重写业务逻辑，只是提供 Agent 工具接口
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ConversationTool implements UnifiedAgentTool {
    
    private final ConversationService conversationService;
    
    @Override
    public ActionResult execute(AgentState state) {
        try {
            log.info("🗣️ ConversationTool executing for session: {}", state.getSessionId());

            ChatResponse deterministicResponse = buildDeterministicFollowUp(state);
            if (deterministicResponse != null) {
                return ActionResult.builder()
                        .toolName("conversation")
                        .success(true)
                        .observation("Guided response: " + deterministicResponse.getMessage())
                        .result(deterministicResponse)
                        .build();
            }
            
            // 调用现有服务（userId 是 Long 类型）
            ChatResponse response = conversationService.chat(
                state.getUserId(), 
                state.getSessionId(), 
                state.getCurrentMessage()
            );
            
            return ActionResult.builder()
                .toolName("conversation")
                .success(true)
                .observation("AI response: " + response.getMessage())
                .result(response)
                .build();
                
        } catch (Exception e) {
            log.error("ConversationTool execution failed", e);
            return ActionResult.builder()
                .toolName("conversation")
                .success(false)
                .observation("Failed to process conversation: " + e.getMessage())
                .error(e.getMessage())
                .build();
        }
    }
    
    @Override
    public String getToolName() {
        return "conversation";
    }
    
    @Override
    public String getDescription() {
        return "Chat with user to collect information or provide responses";
    }

    private ChatResponse buildDeterministicFollowUp(AgentState state) {
        if (state == null) {
            return null;
        }

        String routeReason = getRouteReason(state);
        if (routeReason == null || routeReason.isBlank()) {
            return null;
        }

        if (routeReason.startsWith("missing_recommendation_fields:")) {
            Set<String> missing = parseMissingFields(routeReason);
            if (!missing.isEmpty()) {
                return toChatResponse(state, buildMissingFieldsMessage(state, missing, true));
            }
        }

        if ("awaiting_itinerary_confirmation".equals(routeReason)
                || "clear_destination_skip_recommendation".equals(routeReason)) {
            return toChatResponse(state, buildItineraryConfirmationMessage(state));
        }

        if ("need_more_information".equals(routeReason)) {
            TravelIntent intent = state.getIntent();
            if (intent == null) {
                return null;
            }

            if (isNotBlank(intent.getDestination())) {
                List<String> missing = new ArrayList<>();
                if (intent.getDays() == null) {
                    missing.add("days");
                }
                if (!isNotBlank(intent.getBudget())) {
                    missing.add("budget");
                }
                if (!missing.isEmpty()) {
                    boolean recommendationMode = intent.getType() != TravelIntent.IntentType.DESTINATION_CLEAR;
                    return toChatResponse(
                            state,
                            buildMissingFieldsMessage(state, new LinkedHashSet<>(missing), recommendationMode)
                    );
                }

                if (intent.getType() == TravelIntent.IntentType.DESTINATION_CLEAR) {
                    return toChatResponse(state, buildItineraryConfirmationMessage(state));
                }
            }
        }

        return null;
    }

    private ChatResponse toChatResponse(AgentState state, String message) {
        return ChatResponse.builder()
                .sessionId(state.getSessionId())
                .message(message)
                .timestamp(LocalDateTime.now())
                .intent(state.getIntent())
                .build();
    }

    private String buildMissingFieldsMessage(AgentState state, Set<String> missing, boolean recommendationMode) {
        TravelIntent intent = state.getIntent();
        List<String> labels = new ArrayList<>();
        if (missing.contains("budget")) {
            labels.add("预算（人民币）");
        }
        if (missing.contains("days")) {
            labels.add("旅行天数");
        }
        if (missing.contains("preferences")) {
            labels.add("偏好（如美食/自然/博物馆）");
        }

        StringBuilder message = new StringBuilder();
        String warmOpening = buildWarmOpening(intent, recommendationMode);
        if (isNotBlank(warmOpening)) {
            message.append(warmOpening);
        }

        if (labels.size() == 1) {
            message.append("还差一个关键信息：").append(labels.get(0)).append("。");
        } else {
            message.append(recommendationMode
                    ? "为了给你更贴合的目的地建议，我还需要这几点："
                    : "为了把这次行程排得更顺，我还需要这几点：");
            message.append(String.join("、", labels)).append("。");
        }

        if (intent != null) {
            List<String> known = new ArrayList<>();
            if (isNotBlank(intent.getDestination())) {
                known.add("目的地：" + intent.getDestination());
            }
            if (intent.getDays() != null) {
                known.add("天数：" + intent.getDays() + "天");
            }
            if (isNotBlank(intent.getBudget())) {
                known.add("预算：" + intent.getBudget());
            }
            if (intent.getCompanionType() != null) {
                known.add("同行人：" + formatCompanionType(intent.getCompanionType()));
            }
            if (!known.isEmpty()) {
                message.append("我这边已记录：").append(String.join("，", known)).append("。");
            }
        }

        message.append(recommendationMode
                ? "一句话补充就行，我拿到后马上给你出建议。"
                : "补齐后我就可以直接开始生成行程。");
        if (intent == null || intent.getCompanionType() == null) {
            message.append("如果方便，也可以顺带说下同行人（独自/情侣/家庭）。");
        }
        return message.toString();
    }

    private String buildItineraryConfirmationMessage(AgentState state) {
        TravelIntent intent = state.getIntent();
        String destination = intent != null && isNotBlank(intent.getDestination())
                ? intent.getDestination()
                : "该目的地";
        String days = intent != null && intent.getDays() != null ? intent.getDays() + "天" : "未提供天数";
        String budget = intent != null && isNotBlank(intent.getBudget()) ? intent.getBudget() : "未提供预算";

        StringBuilder message = new StringBuilder();
        String acknowledgement = buildUpdateAcknowledgement(state.getCurrentMessage());
        if (isNotBlank(acknowledgement)) {
            message.append(acknowledgement);
        }

        message.append(String.format("我这边已经整理好：%s，%s，预算%s", destination, days, budget));

        if (intent != null && intent.getInterests() != null && !intent.getInterests().isEmpty()) {
            String interests = intent.getInterests().stream()
                    .map(String::trim)
                    .filter(this::isNotBlank)
                    .limit(3)
                    .reduce((a, b) -> a + "、" + b)
                    .orElse(null);
            if (isNotBlank(interests)) {
                message.append("，偏好").append(interests);
            }
        }
        if (intent != null && intent.getCompanionType() != null) {
            message.append("，同行人").append(formatCompanionType(intent.getCompanionType()));
        }

        message.append("。如果你愿意，我现在就可以直接生成一版行程。");
        message.append("回复“开始规划”或“就这样”都可以；想先微调预算、节奏或必去点也行。");
        return message.toString();
    }

    private String buildWarmOpening(TravelIntent intent, boolean recommendationMode) {
        if (intent == null) {
            return recommendationMode ? "明白啦。" : "收到。";
        }

        if (isNotBlank(intent.getDestination()) && intent.getInterests() != null && !intent.getInterests().isEmpty()) {
            return "这个方向很不错，我大致理解你的玩法了。";
        }
        if (isNotBlank(intent.getDestination())) {
            return recommendationMode ? "收到，你的目的地方向我记下了。" : "收到，目的地我记下了。";
        }
        return recommendationMode ? "我来帮你快速收敛一下选择。" : "我来帮你把信息补齐。";
    }

    private String buildUpdateAcknowledgement(String currentMessage) {
        if (!isNotBlank(currentMessage)) {
            return "收到。";
        }

        String lower = currentMessage.toLowerCase(Locale.ROOT);
        List<String> updated = new ArrayList<>();
        if (hasBudgetSignal(lower)) {
            updated.add("预算");
        }
        if (hasDurationSignal(lower)) {
            updated.add("天数");
        }
        if (hasPreferenceSignal(lower)) {
            updated.add("偏好");
        }
        if (hasCompanionSignal(lower)) {
            updated.add("同行人");
        }

        if (updated.isEmpty()) {
            return "收到。";
        }
        if (updated.size() == 1) {
            return "收到，" + updated.get(0) + "我记下了。";
        }
        return "收到，" + String.join("和", updated) + "我都记下了。";
    }

    private boolean hasBudgetSignal(String message) {
        return message.contains("预算")
                || message.contains("人民币")
                || message.contains("rmb")
                || message.contains("cny")
                || message.matches(".*\\d+[kKwW万]?\\s*(元|块|人民币|rmb|cny|￥).*");
    }

    private boolean hasDurationSignal(String message) {
        return message.contains("天")
                || message.contains("晚")
                || message.contains("day")
                || message.contains("days")
                || message.contains("night")
                || message.contains("nights");
    }

    private boolean hasPreferenceSignal(String message) {
        return message.contains("喜欢")
                || message.contains("偏好")
                || message.contains("想逛")
                || message.contains("美食")
                || message.contains("景点")
                || message.contains("museum")
                || message.contains("food");
    }

    private boolean hasCompanionSignal(String message) {
        return message.contains("同行")
                || message.contains("一个人")
                || message.contains("独自")
                || message.contains("情侣")
                || message.contains("爱人")
                || message.contains("伴侣")
                || message.contains("家庭")
                || message.contains("亲子")
                || message.contains("家人")
                || message.contains("朋友")
                || message.contains("solo")
                || message.contains("couple")
                || message.contains("family")
                || message.contains("friends");
    }

    private String formatCompanionType(TravelIntent.CompanionType companionType) {
        if (companionType == null) {
            return "未提供";
        }
        return switch (companionType) {
            case SOLO -> "独自";
            case COUPLE -> "情侣";
            case FAMILY -> "家庭";
            case FRIENDS -> "朋友";
        };
    }

    private Set<String> parseMissingFields(String routeReason) {
        String raw = routeReason.substring("missing_recommendation_fields:".length());
        if (raw.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .map(s -> s.toLowerCase(Locale.ROOT))
                .filter(s -> !s.isBlank())
                .collect(LinkedHashSet::new, LinkedHashSet::add, LinkedHashSet::addAll);
    }

    private String getRouteReason(AgentState state) {
        Map<String, Object> metadata = state.getMetadata();
        if (metadata == null) {
            return null;
        }
        Object reason = metadata.get("routeReason");
        return reason == null ? null : String.valueOf(reason);
    }

    private boolean isNotBlank(String text) {
        return text != null && !text.isBlank();
    }
}
