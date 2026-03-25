package com.travel.agent.ai.agent.unified;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.travel.agent.dto.TravelIntent;
import com.travel.agent.dto.unified.StateConverter;
import com.travel.agent.dto.unified.UnifiedTravelIntent;
import com.travel.agent.service.AIService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 结构化意图提取器：
 * - LLM 仅做信息抽取（函数调用）
 * - 决策标记由 Java 规则计算
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StructuredIntentExtractor {

    private static final String FUNCTION_NAME = "extract_travel_intent";
    private static final String FUNCTION_DESCRIPTION = "Extract structured travel intent from the latest user message.";
    private static final String INTENT_SCHEMA = """
            {
              "type": "object",
              "additionalProperties": false,
              "properties": {
                "travel_related": { "type": "boolean" },
                "destination": { "type": ["string", "null"] },
                "destination_type": {
                  "type": ["string", "null"],
                  "enum": ["city", "country", "region", "vague", "unknown", null],
                  "description": "city=specific city/place, country=a whole country, region=continent or multi-country area, vague=generic concept like beach/mountains, unknown=unclear"
                },
                "days": { "type": ["integer", "null"], "minimum": 1, "maximum": 30 },
                "budget": { "type": ["string", "null"] },
                "interests": { "type": "array", "items": { "type": "string" } },
                "mood": { "type": ["string", "null"] },
                "companion_type": {
                  "type": ["string", "null"],
                  "enum": ["solo", "couple", "family", "friends", "unknown", null]
                },
                "confidence": { "type": "number", "minimum": 0, "maximum": 1 },
                "user_goal": {
                  "type": "string",
                  "enum": ["chat", "destination_search", "itinerary_generation", "unknown"]
                }
              },
              "required": [
                "travel_related",
                "destination",
                "destination_type",
                "days",
                "budget",
                "interests",
                "mood",
                "companion_type",
                "confidence",
                "user_goal"
              ]
            }
            """;
    private static final Pattern EXPLICIT_DAYS_PATTERN = Pattern.compile(
            "(\\d{1,2})\\s*(天|晚|夜|day|days|night|nights)",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern EXPLICIT_DAYS_RI_TRIP_PATTERN = Pattern.compile(
            "(\\d{1,2})\\s*日\\s*(游|行|行程|旅行|假期|度假)"
    );
    private static final Pattern EXPLICIT_DAYS_VERB_RI_PATTERN = Pattern.compile(
            "(玩|待|住|安排|规划|行程|旅行)\\s*(\\d{1,2})\\s*日"
    );
    private static final Pattern EXPLICIT_WEEKS_PATTERN = Pattern.compile(
            "(\\d{1,2})\\s*(周|星期|week|weeks)",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern EXPLICIT_ONE_WEEK_PATTERN = Pattern.compile("(一周|一星期|1周|1星期|one week)", Pattern.CASE_INSENSITIVE);
    private static final Pattern BUDGET_AMOUNT_PATTERN = Pattern.compile("(\\d{2,9}(?:\\.\\d{1,2})?)\\s*(人民币|rmb|元|块|万|w|k|usd|dollars|刀)?", Pattern.CASE_INSENSITIVE);
    private static final Set<String> COUNTRY_TOKENS = buildCountryTokens();
    private static final Set<String> REGION_TOKENS = buildRegionTokens();

    private final AIService aiService;
    private final ObjectMapper objectMapper;

    public UnifiedTravelIntent extractAndMerge(String userMessage, UnifiedTravelIntent previousIntent) {
        long start = System.currentTimeMillis();
        TravelIntent previousLegacyIntent = StateConverter.toTravelIntent(previousIntent);

        TravelIntent ruleMergedIntent = tryRuleBasedMerge(previousLegacyIntent, userMessage);
        if (ruleMergedIntent != null) {
            log.info("⚡ Intent extracted via rules: duration={}ms, message='{}'",
                    System.currentTimeMillis() - start,
                    abbreviate(userMessage, 40));
            // 规则路径：无 LLM destination_type，使用内部 token 集合推断（覆盖完整 ISO 国家列表）
            String inferredType = inferDestinationTypeFromTokens(ruleMergedIntent.getDestination());
            return mergeToUnified(previousIntent, ruleMergedIntent, inferredType);
        }

        // Rule-based found no new travel info. If a previous intent exists, preserve it —
        // no LLM call needed. The IntentRouter will decide the next action from the current state.
        if (previousIntent != null) {
            log.info("⚡ No new travel info, preserving existing intent ({}ms)", System.currentTimeMillis() - start);
            return previousIntent;
        }

        String prompt = buildExtractionPrompt(userMessage, previousLegacyIntent);
        try {
            String argumentsJson = aiService.chatWithFunctionCall(
                    prompt,
                    FUNCTION_NAME,
                    FUNCTION_DESCRIPTION,
                    INTENT_SCHEMA
            );

            ExtractedIntent payload = objectMapper.readValue(argumentsJson, ExtractedIntent.class);
            TravelIntent mergedLegacyIntent = mergeAndNormalize(previousLegacyIntent, payload, userMessage);
            log.info("🧠 Intent extracted via LLM function call: duration={}ms, message='{}', destination_type={}",
                    System.currentTimeMillis() - start,
                    abbreviate(userMessage, 40),
                    payload.destinationType);
            // LLM 路径：直接使用 LLM 返回的 destination_type（最准确）
            return mergeToUnified(previousIntent, mergedLegacyIntent, payload.destinationType);
        } catch (Exception e) {
            log.warn("Structured intent extraction failed, fallback to previous/default intent: {}", e.getMessage());
            if (previousIntent != null) {
                return previousIntent;
            }
            return UnifiedTravelIntent.createDefault(null, null);
        }
    }

    private TravelIntent tryRuleBasedMerge(TravelIntent previousIntent, String userMessage) {
        if (userMessage == null || userMessage.isBlank()) {
            return null;
        }
        String normalizedMessage = userMessage.trim();
        String lower = normalizedMessage.toLowerCase(Locale.ROOT);

        TravelIntent base = previousIntent != null ? previousIntent : defaultGeneralChatIntent();
        TravelIntent.TravelIntentBuilder builder = TravelIntent.builder()
                .destination(base.getDestination())
                .days(base.getDays())
                .budget(base.getBudget())
                .interests(base.getInterests() != null ? new ArrayList<>(base.getInterests()) : new ArrayList<>())
                .mood(base.getMood())
                .companionType(base.getCompanionType())
                .confidence(base.getConfidence())
                .type(base.getType())
                .needsRecommendation(base.getNeedsRecommendation())
                .readyForItinerary(base.getReadyForItinerary());

        boolean changed = false;

        Integer explicitDays = extractExplicitDaysFromMessage(normalizedMessage);
        if (explicitDays == null) {
            explicitDays = extractWeeksToDays(normalizedMessage);
        }
        if (explicitDays != null) {
            builder.days(explicitDays);
            changed = true;
        }

        String explicitBudget = extractExplicitBudget(normalizedMessage);
        if (explicitBudget != null) {
            builder.budget(explicitBudget);
            changed = true;
        }

        TravelIntent.CompanionType companionType = extractExplicitCompanionTypeFromMessage(normalizedMessage);
        if (companionType != null) {
            builder.companionType(companionType);
            changed = true;
        }

        List<String> interests = extractInterestSignals(lower);
        if (!interests.isEmpty()) {
            builder.interests(interests);
            changed = true;
        }

        String mood = extractMoodSignal(lower);
        if (mood != null) {
            builder.mood(mood);
            changed = true;
        }

        // Try to extract a clean geo token first (avoids storing full sentence as destination)
        String geoToken = extractKnownGeoToken(normalizedMessage, lower);
        if (geoToken != null && !geoToken.equalsIgnoreCase(base.getDestination())) {
            if (!isLikelyAffirmationOrControlMessage(normalizedMessage)) {
                builder.destination(normalizeDestination(geoToken));
                changed = true;
            }
        } else if (shouldTreatAsDestinationUpdate(base, normalizedMessage, explicitBudget, explicitDays, interests, mood)) {
            builder.destination(normalizeDestination(normalizedMessage));
            changed = true;
        }

        if (!changed) {
            return null;
        }

        TravelIntent merged = builder.build();
        applyDeterministicFlags(merged, true);
        return merged;
    }

    private Integer extractWeeksToDays(String message) {
        if (message == null || message.isBlank()) {
            return null;
        }
        Matcher oneWeek = EXPLICIT_ONE_WEEK_PATTERN.matcher(message);
        if (oneWeek.find()) {
            return 7;
        }
        Matcher weekMatcher = EXPLICIT_WEEKS_PATTERN.matcher(message);
        if (weekMatcher.find()) {
            try {
                int weeks = Integer.parseInt(weekMatcher.group(1));
                int days = weeks * 7;
                if (days >= 1 && days <= 30) {
                    return days;
                }
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    private String extractExplicitBudget(String message) {
        if (message == null || message.isBlank()) {
            return null;
        }
        Matcher matcher = BUDGET_AMOUNT_PATTERN.matcher(message);
        if (!matcher.find()) {
            return null;
        }
        String amountStr = matcher.group(1);
        String unit = matcher.group(2) == null ? "" : matcher.group(2).toLowerCase(Locale.ROOT);
        try {
            BigDecimal amount = new BigDecimal(amountStr);
            if ("万".equals(unit) || "w".equals(unit)) {
                amount = amount.multiply(new BigDecimal("10000"));
            } else if ("k".equals(unit)) {
                amount = amount.multiply(new BigDecimal("1000"));
            }
            if (amount.compareTo(new BigDecimal("100")) < 0) {
                return null;
            }
            return amount.toPlainString();
        } catch (Exception ignored) {
            return null;
        }
    }

    private List<String> extractInterestSignals(String lower) {
        if (lower == null || lower.isBlank()) {
            return List.of();
        }
        LinkedHashSet<String> interests = new LinkedHashSet<>();
        if (lower.contains("徒步") || lower.contains("hike") || lower.contains("hiking")) {
            interests.add("hiking");
        }
        if (lower.contains("文化") || lower.contains("museum") || lower.contains("history")) {
            interests.add("culture");
        }
        if (lower.contains("美食") || lower.contains("food")) {
            interests.add("food");
        }
        if (lower.contains("海滩") || lower.contains("沙滩") || lower.contains("beach")
                || lower.contains("冲浪") || lower.contains("surf")) {
            interests.add("beach");
        }
        if (lower.contains("自然") || lower.contains("nature")) {
            interests.add("nature");
        }
        if (lower.contains("动物") || lower.contains("野生") || lower.contains("wildlife")
                || lower.contains("safari") || lower.contains("丛林")) {
            interests.add("wildlife");
        }
        if (lower.contains("购物") || lower.contains("shopping")) {
            interests.add("shopping");
        }
        return new ArrayList<>(interests);
    }

    private String extractMoodSignal(String lower) {
        if (lower == null || lower.isBlank()) {
            return null;
        }
        if (lower.contains("放松") || lower.contains("relax")) {
            return "relaxing";
        }
        if (lower.contains("冒险") || lower.contains("adventure")) {
            return "adventure";
        }
        if (lower.contains("文化") || lower.contains("cultural")) {
            return "cultural";
        }
        return null;
    }

    /**
     * Tries to find a known geo token (region or country) in the message and returns just that token.
     * Returns null if no known token is found.
     */
    private String extractKnownGeoToken(String message, String lower) {
        if (message == null || message.isBlank()) {
            return null;
        }
        // Check REGION_TOKENS first (higher priority — more specific regions like 东南亚)
        for (String token : REGION_TOKENS) {
            if (token.length() >= 2 && lower.contains(token)) {
                int idx = lower.indexOf(token);
                return message.substring(idx, idx + token.length());
            }
        }
        // Check COUNTRY_TOKENS (only ≥2 chars to avoid single-char false positives)
        for (String token : COUNTRY_TOKENS) {
            if (token.length() >= 2 && lower.contains(token)) {
                int idx = lower.indexOf(token);
                return message.substring(idx, idx + token.length());
            }
        }
        return null;
    }

    private boolean shouldTreatAsDestinationUpdate(
            TravelIntent baseIntent,
            String message,
            String explicitBudget,
            Integer explicitDays,
            List<String> interests,
            String mood
    ) {
        if (message == null || message.isBlank()) {
            return false;
        }
        if (explicitBudget != null || explicitDays != null || (interests != null && !interests.isEmpty()) || mood != null) {
            return false;
        }
        if (message.length() > 20) {
            return false;
        }
        String lower = message.toLowerCase(Locale.ROOT);
        if (isLikelyAffirmationOrControlMessage(message)) {
            return false;
        }
        if (lower.contains("多少钱")
                && !lower.contains("预算")
                && !lower.contains("几天")) {
            return false;
        }

        boolean hasGeoCue = hasExplicitGeoCue(message, lower);
        String existingDestination = baseIntent != null ? baseIntent.getDestination() : null;

        // 已有目的地时，只有出现明确地理线索才允许改目的地，避免“开始吧/可以”等短语污染 slot。
        if (isMeaningful(existingDestination)) {
            return hasGeoCue;
        }

        // 首次填目的地：必须要有地理线索，或输入本身是典型地名 token（如“摩洛哥”“北非”）。
        return hasGeoCue || looksLikeStandalonePlaceToken(message);
    }

    /**
     * Returns true if the message contains no geo cues and is short enough to be a control phrase
     * rather than a place name. Used to guard against treating short confirmations as destinations.
     */
    private boolean isLikelyAffirmationOrControlMessage(String message) {
        if (message == null || message.isBlank()) {
            return false;
        }
        String lower = message.toLowerCase(Locale.ROOT);
        // Any explicit start/confirm trigger is definitely not a destination name
        return lower.contains("开始") || lower.contains("确认") || lower.contains("继续")
                || lower.equals("好") || lower.equals("好的") || lower.equals("行")
                || lower.equals("可以") || lower.equals("是") || lower.equals("是的")
                || lower.equals("ok") || lower.equals("okay") || lower.equals("yes");
    }

    private boolean hasExplicitGeoCue(String message, String lower) {
        if (message == null || message.isBlank()) {
            return false;
        }
        if (lower.contains("想去")
                || lower.contains("去")
                || lower.contains("到")
                || lower.contains("在")
                || lower.contains("travel to")
                || lower.contains("visit")
                || lower.contains("in ")
                || lower.contains("to ")) {
            return true;
        }
        String normalized = normalizeGeoToken(message);
        if (COUNTRY_TOKENS.contains(normalized) || REGION_TOKENS.contains(normalized)) {
            return true;
        }
        return lower.contains("国")
                || lower.contains("洲")
                || lower.contains("省")
                || lower.contains("州")
                || lower.contains("市")
                || lower.contains("岛")
                || lower.contains("海岸")
                || lower.contains("草原")
                || lower.contains("沙漠");
    }

    private boolean looksLikeStandalonePlaceToken(String message) {
        if (message == null || message.isBlank()) {
            return false;
        }
        String normalized = normalizeGeoToken(message);
        if (normalized.isBlank()) {
            return false;
        }
        if (COUNTRY_TOKENS.contains(normalized) || REGION_TOKENS.contains(normalized)) {
            return true;
        }
        // 兼容简短地名（如“摩洛哥”“北非”）
        return message.length() <= 8 && !isLikelyAffirmationOrControlMessage(message);
    }

    private String abbreviate(String message, int max) {
        if (message == null) {
            return "";
        }
        return message.length() <= max ? message : message.substring(0, max) + "...";
    }

    /**
     * 将 TravelIntent 合并转换为 UnifiedTravelIntent，并应用 LLM 或内部推断的目的地类型。
     *
     * <p>destinationType 来源优先级：
     * <ol>
     *   <li>LLM 路径：直接使用 LLM Function Calling 返回的 {@code destinationType}（最准确）</li>
     *   <li>规则路径：使用 {@link #inferDestinationTypeFromTokens} 基于完整 ISO 国家 token 集合推断</li>
     *   <li>兜底：保留 {@link StateConverter} 的推断结果（仅覆盖 8 个大国，已知不完整，不依赖）</li>
     * </ol>
     */
    private UnifiedTravelIntent mergeToUnified(
            UnifiedTravelIntent previousIntent,
            TravelIntent mergedLegacyIntent,
            String llmDestinationType
    ) {
        Long userId = previousIntent != null ? previousIntent.getUserId() : null;
        String sessionId = previousIntent != null ? previousIntent.getSessionId() : null;
        UnifiedTravelIntent converted = StateConverter.fromTravelIntent(mergedLegacyIntent, userId, sessionId);

        // 将 LLM 或 token 推断的目的地类型映射为枚举并覆盖 StateConverter 的推断
        UnifiedTravelIntent.DestinationType resolvedType = mapToDestinationType(llmDestinationType);

        if (previousIntent == null) {
            return resolvedType != null
                    ? converted.toBuilder().destinationType(resolvedType).build()
                    : converted;
        }

        UnifiedTravelIntent.UnifiedTravelIntentBuilder builder = converted.toBuilder()
                .sessionId(previousIntent.getSessionId())
                .userId(previousIntent.getUserId())
                .destinationCountry(previousIntent.getDestinationCountry())
                .destinationLatitude(previousIntent.getDestinationLatitude())
                .destinationLongitude(previousIntent.getDestinationLongitude())
                .partySize(previousIntent.getPartySize())
                .travelStyle(previousIntent.getTravelStyle());

        if (resolvedType != null) {
            builder.destinationType(resolvedType);
        }

        return builder.build();
    }

    /**
     * 将字符串形式的目的地类型（LLM 输出）映射为枚举值。
     * 返回 null 表示无有效输入，调用方应保留已有值。
     */
    private UnifiedTravelIntent.DestinationType mapToDestinationType(String raw) {
        if (raw == null || raw.isBlank()) return null;
        return switch (raw.trim().toLowerCase(Locale.ROOT)) {
            case "city"    -> UnifiedTravelIntent.DestinationType.CITY;
            case "country" -> UnifiedTravelIntent.DestinationType.COUNTRY;
            case "region"  -> UnifiedTravelIntent.DestinationType.REGION;
            case "vague"   -> UnifiedTravelIntent.DestinationType.VAGUE;
            default        -> UnifiedTravelIntent.DestinationType.UNKNOWN;
        };
    }

    /**
     * 规则路径的目的地类型推断：使用与 {@link #isCountryLevelDestination} 相同的完整 ISO 国家 token 集合，
     * 覆盖 StateConverter 中只有 8 个大国的不完整硬编码列表。
     *
     * @return "country" / "region" / null（null 表示可能是城市或未知，交由 StateConverter 兜底）
     */
    private String inferDestinationTypeFromTokens(String destination) {
        if (destination == null || destination.isBlank()) return null;
        String normalized = normalizeGeoToken(destination);
        if (REGION_TOKENS.contains(normalized))  return "region";
        if (COUNTRY_TOKENS.contains(normalized)) return "country";
        return null;
    }

    private String buildExtractionPrompt(String userMessage, TravelIntent previousIntent) {
        String previous = "null";
        try {
            previous = previousIntent == null ? "null" : objectMapper.writeValueAsString(previousIntent);
        } catch (Exception ignored) {
        }

        return """
                Extract travel intent only from the latest user message.
                Do not decide which tool to call.
                Do not include explanatory text.

                Latest user message:
                \"%s\"

                Previous structured intent (for slot-filling reference only):
                %s

                Rules:
                1) Keep missing fields as null (do not hallucinate).
                2) Use integers for days.
                3) Keep budget as a short string (e.g. "8000", "10000").
                4) interests must be an array (empty array allowed).
                5) companion_type should be one of: solo/couple/family/friends/unknown/null.
                6) travel_related=false only for non-travel casual chat.
                7) destination_type classification:
                   - "city"    → a specific city or named place (Tokyo, Paris, Machu Picchu, Bali)
                   - "country" → an entire country (Japan, France, Thailand, Morocco)
                   - "region"  → a continent or multi-country area (Southeast Asia, Europe, East Africa)
                   - "vague"   → a generic geographic concept (beach, mountains, island, tropical)
                   - "unknown" → destination is not mentioned or completely unclear
                   Set destination_type=null only when destination is null.
                """.formatted(userMessage, previous);
    }

    private TravelIntent mergeAndNormalize(TravelIntent previousIntent, ExtractedIntent payload, String userMessage) {
        TravelIntent base = previousIntent != null ? previousIntent : defaultGeneralChatIntent();
        TravelIntent.TravelIntentBuilder builder = TravelIntent.builder()
                .destination(base.getDestination())
                .days(base.getDays())
                .budget(base.getBudget())
                .interests(base.getInterests() != null ? new ArrayList<>(base.getInterests()) : new ArrayList<>())
                .mood(base.getMood())
                .companionType(base.getCompanionType())
                .confidence(base.getConfidence())
                .type(base.getType())
                .needsRecommendation(base.getNeedsRecommendation())
                .readyForItinerary(base.getReadyForItinerary());

        boolean hasExplicitUpdates = hasExplicitTravelUpdates(payload);
        if (Boolean.FALSE.equals(payload.travelRelated) && !hasExplicitUpdates && previousIntent != null) {
            return previousIntent;
        }

        if (isMeaningful(payload.destination)) {
            builder.destination(normalizeDestination(payload.destination));
        }

        Integer explicitDays = extractExplicitDaysFromMessage(userMessage);
        if (explicitDays != null) {
            builder.days(explicitDays);
        } else if (payload.days != null && shouldAcceptModelDaysUpdate(userMessage, previousIntent)) {
            builder.days(payload.days);
        }

        if (isMeaningful(payload.budget)) {
            builder.budget(normalizeBudget(payload.budget));
        }

        if (payload.interests != null && !payload.interests.isEmpty()) {
            builder.interests(payload.interests.stream()
                    .map(String::trim)
                    .filter(s -> !s.isBlank())
                    .toList());
        }

        if (isMeaningful(payload.mood)) {
            builder.mood(payload.mood.trim());
        }

        TravelIntent.CompanionType explicitCompanion = extractExplicitCompanionTypeFromMessage(userMessage);
        if (explicitCompanion != null) {
            builder.companionType(explicitCompanion);
        } else {
            TravelIntent.CompanionType extractedCompanion = normalizeCompanionType(payload.companionType);
            if (extractedCompanion != null) {
                builder.companionType(extractedCompanion);
            }
        }

        Double confidence = payload.confidence != null ? payload.confidence : base.getConfidence();
        builder.confidence(clampConfidence(confidence));

        TravelIntent merged = builder.build();
        applyDeterministicFlags(merged, payload.travelRelated);
        return merged;
    }

    private void applyDeterministicFlags(TravelIntent intent, Boolean travelRelated) {
        boolean hasDestination = isMeaningful(intent.getDestination());
        boolean hasCompanion = intent.getCompanionType() != null;
        boolean clearDestination = hasDestination
                && !isVagueDestination(intent.getDestination())
                && !isCountryLevelDestination(intent.getDestination());
        boolean hasPreferences = (intent.getInterests() != null && !intent.getInterests().isEmpty())
                || isMeaningful(intent.getMood())
                || intent.getDays() != null
                || isMeaningful(intent.getBudget());
        boolean hasTravelSignals = hasDestination || hasPreferences || hasCompanion || Boolean.TRUE.equals(travelRelated);

        if (!hasTravelSignals) {
            intent.setType(TravelIntent.IntentType.GENERAL_CHAT);
            intent.setNeedsRecommendation(false);
            intent.setReadyForItinerary(false);
            return;
        }

        if (clearDestination) {
            intent.setType(TravelIntent.IntentType.DESTINATION_CLEAR);
            intent.setReadyForItinerary(intent.getDays() != null && isMeaningful(intent.getBudget()));
            intent.setNeedsRecommendation(false);
            return;
        }

        if (!hasDestination && !hasPreferences) {
            intent.setType(TravelIntent.IntentType.CONTINUE_CONVERSATION);
            intent.setNeedsRecommendation(false);
            intent.setReadyForItinerary(false);
            return;
        }

        intent.setType(TravelIntent.IntentType.DESTINATION_UNCLEAR);
        intent.setNeedsRecommendation(hasPreferences || hasDestination);
        intent.setReadyForItinerary(false);
    }

    private boolean hasExplicitTravelUpdates(ExtractedIntent payload) {
        return isMeaningful(payload.destination)
                || payload.days != null
                || isMeaningful(payload.budget)
                || (payload.interests != null && !payload.interests.isEmpty())
                || isMeaningful(payload.mood)
                || normalizeCompanionType(payload.companionType) != null;
    }

    private TravelIntent.CompanionType normalizeCompanionType(String rawCompanionType) {
        if (!isMeaningful(rawCompanionType)) {
            return null;
        }

        String normalized = rawCompanionType.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "solo", "single", "alone", "独自", "独行", "一个人" -> TravelIntent.CompanionType.SOLO;
            case "couple", "romantic", "情侣", "爱人", "伴侣", "夫妻" -> TravelIntent.CompanionType.COUPLE;
            case "family", "家庭", "亲子", "带娃", "家人" -> TravelIntent.CompanionType.FAMILY;
            case "friends", "friend", "朋友", "闺蜜" -> TravelIntent.CompanionType.FRIENDS;
            default -> null;
        };
    }

    private TravelIntent.CompanionType extractExplicitCompanionTypeFromMessage(String message) {
        if (message == null || message.isBlank()) {
            return null;
        }

        String lower = message.toLowerCase(Locale.ROOT);
        if (lower.contains("家庭")
                || lower.contains("一家")
                || lower.contains("家人")
                || lower.contains("亲子")
                || lower.contains("带娃")
                || lower.contains("父母")
                || lower.contains("孩子")) {
            return TravelIntent.CompanionType.FAMILY;
        }
        if (lower.contains("情侣")
                || lower.contains("爱人")
                || lower.contains("伴侣")
                || lower.contains("对象")
                || lower.contains("男朋友")
                || lower.contains("女朋友")
                || lower.contains("老公")
                || lower.contains("老婆")
                || lower.contains("夫妻")
                || lower.contains("honeymoon")
                || lower.contains("romantic")) {
            return TravelIntent.CompanionType.COUPLE;
        }
        if (lower.contains("独自")
                || lower.contains("独行")
                || lower.contains("一个人")
                || lower.contains("solo")
                || lower.contains("alone")) {
            return TravelIntent.CompanionType.SOLO;
        }
        if (lower.contains("朋友")
                || lower.contains("闺蜜")
                || lower.contains("哥们")
                || lower.contains("同事")
                || lower.contains("buddy")
                || lower.contains("friends")) {
            return TravelIntent.CompanionType.FRIENDS;
        }
        return null;
    }

    private String normalizeDestination(String destination) {
        if (!isMeaningful(destination)) {
            return null;
        }
        String normalized = destination.trim();
        if ("unknown".equalsIgnoreCase(normalized) || "unclear".equalsIgnoreCase(normalized)) {
            return null;
        }
        return normalized;
    }

    private String normalizeBudget(String budget) {
        if (!isMeaningful(budget)) {
            return null;
        }
        String cleaned = budget.replaceAll("[^0-9.]", "");
        if (cleaned.isBlank()) {
            return budget.trim();
        }
        try {
            return new BigDecimal(cleaned).toPlainString();
        } catch (Exception ignored) {
            return budget.trim();
        }
    }

    private boolean isMeaningful(String value) {
        return value != null
                && !value.isBlank()
                && !"unknown".equalsIgnoreCase(value.trim())
                && !"unclear".equalsIgnoreCase(value.trim())
                && !"null".equalsIgnoreCase(value.trim());
    }

    private Integer extractExplicitDaysFromMessage(String message) {
        if (message == null || message.isBlank()) {
            return null;
        }
        Matcher matcher = EXPLICIT_DAYS_PATTERN.matcher(message);
        if (matcher.find()) {
            return parseDaysValue(matcher.group(1));
        }

        Matcher riTripMatcher = EXPLICIT_DAYS_RI_TRIP_PATTERN.matcher(message);
        if (riTripMatcher.find()) {
            return parseDaysValue(riTripMatcher.group(1));
        }

        Matcher verbRiMatcher = EXPLICIT_DAYS_VERB_RI_PATTERN.matcher(message);
        if (verbRiMatcher.find()) {
            return parseDaysValue(verbRiMatcher.group(2));
        }
        return null;
    }

    private boolean shouldAcceptModelDaysUpdate(String userMessage, TravelIntent previousIntent) {
        if (previousIntent == null || previousIntent.getDays() == null) {
            return true;
        }
        if (userMessage == null || userMessage.isBlank()) {
            return false;
        }
        if (extractExplicitDaysFromMessage(userMessage) != null) {
            return true;
        }

        String lower = userMessage.toLowerCase(Locale.ROOT);
        return lower.contains("几天")
                || lower.contains("多少天")
                || lower.contains("多待")
                || lower.contains("duration")
                || lower.contains("length of stay");
    }

    private Integer parseDaysValue(String rawValue) {
        try {
            int days = Integer.parseInt(rawValue);
            if (days >= 1 && days <= 30) {
                return days;
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private Double clampConfidence(Double confidence) {
        if (confidence == null) {
            return 0.5;
        }
        return Math.max(0.0, Math.min(1.0, confidence));
    }

    private TravelIntent defaultGeneralChatIntent() {
        return TravelIntent.builder()
                .type(TravelIntent.IntentType.GENERAL_CHAT)
                .interests(new ArrayList<>())
                .needsRecommendation(false)
                .readyForItinerary(false)
                .confidence(0.5)
                .build();
    }

    private boolean isVagueDestination(String destination) {
        String lower = destination.toLowerCase(Locale.ROOT);
        String normalized = normalizeGeoToken(destination);
        String[] vague = {
                "europe", "asia", "africa", "america", "oceania",
                "欧洲", "亚洲", "非洲", "美洲", "大洋洲",
                "beach", "mountain", "island", "city", "somewhere",
                "海滩", "山", "岛", "城市", "某地"
        };
        for (String token : vague) {
            if (lower.contains(token)) {
                return true;
            }
        }
        if (REGION_TOKENS.contains(normalized)) {
            return true;
        }
        if (lower.contains("地区") || lower.contains("区域")) {
            return true;
        }
        if (containsDirectionalRegionSignal(lower)) {
            return true;
        }
        return looksLikeSubRegion(normalized);
    }

    private boolean containsDirectionalRegionSignal(String lower) {
        // 中文方位区域表达：如“美国东部/日本关西/法国南部”
        if (lower.contains("东部")
                || lower.contains("西部")
                || lower.contains("南部")
                || lower.contains("北部")
                || lower.contains("中部")
                || lower.contains("东北")
                || lower.contains("西北")
                || lower.contains("东南")
                || lower.contains("西南")) {
            return true;
        }

        // 英文方位区域表达：如“east coast”, “northern italy”, “west us”
        return lower.matches(".*\\b(east|west|north|south|central|northeast|northwest|southeast|southwest)\\b.*");
    }

    private boolean looksLikeSubRegion(String normalized) {
        if (normalized == null || normalized.isBlank()) {
            return false;
        }
        // 例如：东南亚、东亚、北欧、拉美等区域级表达
        return normalized.length() <= 3
                && (normalized.endsWith("亚")
                || normalized.endsWith("欧")
                || normalized.endsWith("美")
                || normalized.endsWith("非"));
    }

    private boolean isCountryLevelDestination(String destination) {
        if (!isMeaningful(destination)) {
            return false;
        }
        String normalized = normalizeGeoToken(destination);
        if (normalized.isBlank()) {
            return false;
        }
        return COUNTRY_TOKENS.contains(normalized);
    }

    private static Set<String> buildCountryTokens() {
        Set<String> tokens = new HashSet<>();
        for (String code : Locale.getISOCountries()) {
            Locale locale = new Locale("", code);
            addCountryToken(tokens, locale.getDisplayCountry(Locale.ENGLISH));
            addCountryToken(tokens, locale.getDisplayCountry(Locale.CHINESE));
            addCountryToken(tokens, locale.getDisplayCountry(Locale.SIMPLIFIED_CHINESE));
            addCountryToken(tokens, locale.getDisplayCountry(Locale.TRADITIONAL_CHINESE));
        }

        // Common aliases / abbreviations
        String[] aliases = {
                "usa", "us", "u.s.a", "u.s.", "united states",
                "uk", "u.k.", "united kingdom",
                "uae", "u.a.e", "united arab emirates",
                "中国", "中国大陆", "香港", "澳门", "台湾"
        };
        for (String alias : aliases) {
            addCountryToken(tokens, alias);
        }
        return tokens;
    }

    private static Set<String> buildRegionTokens() {
        Set<String> tokens = new HashSet<>();
        String[] regionAliases = {
                // Continents (Chinese)
                "非洲", "亚洲", "欧洲", "美洲", "北美洲", "南美洲", "大洋洲", "南极洲",
                // Continents (English)
                "africa", "asia", "europe", "oceania", "antarctica",
                "north america", "south america", "latin america",
                // Sub-regions (Chinese)
                "东南亚", "东亚", "南亚", "西亚", "中亚", "东北亚",
                "中东", "拉美", "北非", "西非", "东非", "南非地区",
                "西欧", "东欧", "北欧", "南欧", "中欧",
                "北美", "中美", "加勒比",
                // Sub-regions (English)
                "southeast asia", "south east asia",
                "east asia", "south asia", "central asia", "west asia",
                "middle east", "north africa", "west africa", "east africa",
                "eastern europe", "western europe", "northern europe", "southern europe", "central europe",
                "caribbean", "central america"
        };
        for (String alias : regionAliases) {
            addCountryToken(tokens, alias);
        }
        return tokens;
    }

    private static void addCountryToken(Set<String> tokens, String raw) {
        if (raw == null || raw.isBlank()) {
            return;
        }
        String normalized = normalizeGeoToken(raw);
        if (!normalized.isBlank()) {
            tokens.add(normalized);
        }
    }

    private static String normalizeGeoToken(String value) {
        return value.toLowerCase(Locale.ROOT)
                .replaceAll("[\\s\\p{Punct}·（）()]+", "");
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class ExtractedIntent {
        @JsonProperty("travel_related")
        public Boolean travelRelated;
        public String destination;
        public Integer days;
        public String budget;
        public List<String> interests;
        public String mood;
        @JsonProperty("companion_type")
        public String companionType;
        public Double confidence;
        @JsonProperty("user_goal")
        public String userGoal;
        @JsonProperty("destination_type")
        public String destinationType;
    }
}
