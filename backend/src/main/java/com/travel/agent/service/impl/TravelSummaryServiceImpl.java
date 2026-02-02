package com.travel.agent.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.travel.agent.dto.response.TravelSummaryResponse;
import com.travel.agent.entity.ItineraryItems;
import com.travel.agent.entity.Trips;
import com.travel.agent.exception.BusinessException;
import com.travel.agent.service.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class TravelSummaryServiceImpl implements TravelSummaryService {

    private final TripsService tripsService;
    private final ItineraryItemsService itineraryItemsService;
    private final AIService aiService;

    /**
     * 仅生成summary（分天AI日记）并持久化到 trips.ai_summary
     */
    @Override
    public TravelSummaryResponse generateSummary(Long tripId) {
        Trips trip = tripsService.getById(tripId);
        if (trip == null) {
            throw new BusinessException("Trip not found: " + tripId);
        }
        List<ItineraryItems> items = itineraryItemsService.list(
            new LambdaQueryWrapper<ItineraryItems>().eq(ItineraryItems::getTripId, tripId)
        );
        String prompt = buildSummaryPrompt(trip, items);
        String textSummary = aiService.chat(prompt);
        String shareTitle = String.format("My trip to %s", Objects.toString(trip.getDestinationName(), "Destination"));
        String shareText = textSummary;
        // 持久化AI Summary到DB（Regenerate时覆盖）
        try {
            trip.setAiSummary(textSummary);
            tripsService.updateById(trip);
            log.info("[summary.persist] tripId={}, saved ai_summary length={}", tripId, textSummary == null ? 0 : textSummary.length());
        } catch (Exception e) {
            log.warn("[summary.persist] failed to save ai_summary, tripId={}, err={}", tripId, e.getMessage());
        }
        TravelSummaryResponse resp = new TravelSummaryResponse();
        resp.setTripId(tripId);
        resp.setTextSummary(textSummary);
        resp.setPhotoAnalyses(null);
        resp.setShareTitle(shareTitle);
        resp.setShareText(shareText);
        return resp;
    }

    /**
     * 构建 AI 大模型生成每日分段日记摘要的 prompt
     * @param trip  当前行程实体
     * @param items 行程下所有日程活动
     * @return prompt字符串，严格markdown ## Day N\n段落格式
     */
    private String buildSummaryPrompt(Trips trip, List<ItineraryItems> items) {
        StringBuilder sb = new StringBuilder();
        // 前置AI强指令（INS/Twitter 语气，简洁 + 少量 emoji）
        sb.append("You are a travel microblog editor for Instagram/Twitter.\n");
        sb.append("Write concise daily posts in English. For each day, output 3-4 short sentences (each <= 45 words). Highlight 1-2 moments and add 1-2 sentences about mood and travel scenes.\n");
        sb.append("Use 2-3 tasteful emojis inline (e.g., ✈️🌅🍜🏖️🏞️🏙️🖼️🎟️). No hashtags, no bullet lists, no intro or outro.\n");
        sb.append("Format strictly as markdown with a heading then the text:\n");
        sb.append("## Day 1\n<3-4 concise sentences with emojis>\n\n## Day 2\n<3-4 concise sentences with emojis>\n\n");
        sb.append("Be natural and friendly (present tense when possible). Be brief and avoid filler to save tokens.\n\n");
        // 行程元信息
        sb.append("Destination: ").append(Objects.toString(trip.getDestinationName(), ""))
          .append(", ").append(Objects.toString(trip.getDestinationCountry(), "")).append(".\n");
        sb.append("Trip duration: from ").append(trip.getStartDate()).append(" to ").append(trip.getEndDate()).append(".\n");
        // 按天分组 (dayId为Long)
        java.util.Map<Long, List<ItineraryItems>> itemsByDay = items.stream()
            .filter(i -> i.getDayId() != null)
            .collect(java.util.stream.Collectors.groupingBy(ItineraryItems::getDayId, java.util.TreeMap::new, java.util.stream.Collectors.toList()));
        int dayIdx = 1;
        for (List<ItineraryItems> dayItems : itemsByDay.values()) {
            sb.append("\n## Day ").append(dayIdx).append("\n");
            // 可选：给AI一点活动内容梗概，作为写段落线索
            for (ItineraryItems i : dayItems) {
                sb.append(i.getActivityName());
                if (i.getActivityType() != null && !i.getActivityType().isEmpty()) sb.append(" [").append(i.getActivityType()).append("]");
                if (i.getLocation() != null && !i.getLocation().isEmpty()) sb.append(" @").append(i.getLocation());
                sb.append("; ");
            }
            sb.append("\n");
            dayIdx++;
        }
        return sb.toString();
    }
}


