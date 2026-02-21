package com.travel.agent.service.impl;

import com.travel.agent.ai.memory.SessionChatMemoryProvider;
import com.travel.agent.dto.TravelIntent;
import com.travel.agent.dto.response.ChatResponse;
import com.travel.agent.entity.ConversationHistory;
import com.travel.agent.mapper.ConversationHistoryMapper;
import com.travel.agent.service.AIService;
import com.travel.agent.service.ConversationService;
import com.travel.agent.service.IntentAnalysisService;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.ChatMemory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 多轮对话服务实现
 * 基于LangChain4j ChatMemory实现上下文记忆
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConversationServiceImpl implements ConversationService {

    private final AIService aiService;
    private final SessionChatMemoryProvider memoryProvider;
    private final ConversationHistoryMapper conversationHistoryMapper;
    private final IntentAnalysisService intentAnalysisService;

    @Override
    public ChatResponse chat(Long userId, String sessionId, String userMessage) {
        log.info("💬 Chat request: userId={}, sessionId={}, message='{}'", 
                userId, sessionId, userMessage);

        // 1. 如果没有sessionId，创建新会话
        boolean isFirstMessage = (sessionId == null || sessionId.isEmpty());
        if (isFirstMessage) {
            sessionId = UUID.randomUUID().toString();
            log.info("Created new session: {}", sessionId);
        }

        // 2. 获取该会话的ChatMemory
        ChatMemory memory = memoryProvider.get(sessionId);

        // 3. 添加用户消息到memory
        memory.add(UserMessage.from(userMessage));

        // 4. 意图识别（首次对话）
        TravelIntent intent = null;
        if (isFirstMessage) {
            intent = intentAnalysisService.analyzeIntent(userMessage);
            log.info("🎯 Intent analyzed: type={}, destination={}", 
                    intent.getType(), intent.getDestination());
        }

        // 5. 构建包含历史和意图的prompt（不使用RAG）
        String prompt = buildConversationPrompt(memory.messages(), userMessage, intent);

        // 6. 调用AIService生成回答
        String aiResponse = aiService.chat(prompt);

        // 7. 添加AI回答到memory (作为AiMessage)
        memory.add(dev.langchain4j.data.message.AiMessage.from(aiResponse));

        // 8. 保存userId到数据库（通过ChatMemoryStore自动保存消息）
        saveConversationMetadata(userId, sessionId);

        // 9. 不再基于文本摘要重复推断意图，结构化意图由 Agent 的 session state 持久化维护

        log.info("✅ Chat response generated for session: {}", sessionId);

        return ChatResponse.builder()
                .sessionId(sessionId)
                .message(aiResponse)
                .timestamp(LocalDateTime.now())
                .intent(intent)  // 返回意图（首次或重新分析后）
                .build();
    }

    /**
     * 构建对话prompt（专注于意图收集和对话引导）
     */
    private String buildConversationPrompt(List<ChatMessage> messages, String currentMessage, 
                                           TravelIntent intent) {
        StringBuilder prompt = new StringBuilder();
        
        // 系统提示词
        prompt.append("You are an experienced travel consultant. ");
        prompt.append("Reply in the same language as the user (default Simplified Chinese). ");
        prompt.append("Sound natural, warm, and proactive.\n");
        prompt.append("Conversation style rules:\n");
        prompt.append("1) Start with a short acknowledgement of what user just said.\n");
        prompt.append("2) Avoid form-like or robotic wording.\n");
        prompt.append("3) Ask at most ONE most important follow-up question per turn.\n");
        prompt.append("4) If destination + days + budget are already clear, offer to start itinerary directly and give one optional refinement.\n");
        prompt.append("5) Keep responses concise (2-4 short sentences).\n");
        prompt.append("6) Never ask user to repeat information already provided in history.\n\n");
        
        // 意图信息（首次对话）
        if (intent != null) {
            prompt.append("=== User Intent Analysis ===\n");
            if (intent.getType() == TravelIntent.IntentType.DESTINATION_CLEAR) {
                prompt.append("User has a clear destination: ").append(intent.getDestination()).append("\n");
                if (intent.getDays() != null) {
                    prompt.append("Duration: ").append(intent.getDays()).append(" days\n");
                }
                prompt.append("Suggestion: Ask about their interests and preferences to help plan the itinerary.\n");
            } else if (intent.getType() == TravelIntent.IntentType.DESTINATION_UNCLEAR) {
                prompt.append("User needs destination recommendations.\n");
                if (!intent.getInterests().isEmpty()) {
                    prompt.append("Interests: ").append(String.join(", ", intent.getInterests())).append("\n");
                }
                prompt.append("Suggestion: Ask clarifying questions to understand their preferences better.\n");
            }
            prompt.append("\n");
        }
        
        // 对话历史
        if (messages.size() > 1) {
            prompt.append("=== Conversation History ===\n");
            for (ChatMessage msg : messages) {
                if (msg instanceof UserMessage) {
                    prompt.append("User: ").append(((UserMessage) msg).singleText()).append("\n");
                } else if (msg instanceof dev.langchain4j.data.message.AiMessage) {
                    prompt.append("Assistant: ").append(((dev.langchain4j.data.message.AiMessage) msg).text()).append("\n");
                }
            }
            prompt.append("\n");
        }
        
        // 当前问题
        prompt.append("=== Current Question ===\n");
        prompt.append("User: ").append(currentMessage).append("\n\n");
        prompt.append("Assistant:");
        
        return prompt.toString();
    }

    @Override
    public List<ConversationHistory> getHistory(Long userId, String sessionId) {
        return conversationHistoryMapper.selectList(
            new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ConversationHistory>()
                .eq(ConversationHistory::getSessionId, sessionId)
                .orderByAsc(ConversationHistory::getCreatedAt)
        );
    }

    @Override
    public void clearHistory(Long userId, String sessionId) {
        memoryProvider.clear(sessionId);
        log.info("Cleared conversation history for session: {}", sessionId);
    }

    /**
     * 保存对话元数据
     */
    private void saveConversationMetadata(Long userId, String sessionId) {
        // ChatMemoryStore会自动保存消息
        // 这里可以添加额外的元数据保存逻辑
        log.debug("Conversation metadata saved for user: {}, session: {}", userId, sessionId);
    }
}
