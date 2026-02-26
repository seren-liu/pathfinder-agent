package com.travel.agent.ai.agent.unified;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.travel.agent.dto.TravelIntent;
import com.travel.agent.service.AIService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
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
    private static final Set<String> COUNTRY_TOKENS = buildCountryTokens();
    private static final Set<String> REGION_TOKENS = buildRegionTokens();

    private final AIService aiService;
    private final ObjectMapper objectMapper;

    public TravelIntent extractAndMerge(String userMessage, TravelIntent previousIntent) {
        String prompt = buildExtractionPrompt(userMessage, previousIntent);
        try {
            String argumentsJson = aiService.chatWithFunctionCall(
                    prompt,
                    FUNCTION_NAME,
                    FUNCTION_DESCRIPTION,
                    INTENT_SCHEMA
            );

            ExtractedIntent payload = objectMapper.readValue(argumentsJson, ExtractedIntent.class);
            return mergeAndNormalize(previousIntent, payload, userMessage);
        } catch (Exception e) {
            log.warn("Structured intent extraction failed, fallback to previous/default intent: {}", e.getMessage());
            return previousIntent != null ? previousIntent : defaultGeneralChatIntent();
        }
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
                "southeast asia", "south east asia",
                "east asia", "south asia", "central asia", "west asia",
                "middle east", "latin america", "north africa",
                "eastern europe", "western europe", "northern europe", "southern europe",
                "东南亚", "东亚", "南亚", "西亚", "中亚", "东北亚",
                "中东", "拉美", "北非", "西欧", "东欧", "北欧", "南欧"
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
    }
}
