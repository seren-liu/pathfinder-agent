package com.travel.agent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;
import java.time.LocalDateTime;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

/**
 * <p>
 * AI conversation history
 * </p>
 *
 * @author Seren
 * @since 2025-10-18
 */
@Getter
@Setter
@TableName("conversation_history")
@Schema(name = "ConversationHistory", description = "AI conversation history")
public class ConversationHistory implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    @Schema(description = "Conversation ID")
    private Long id;

    @Schema(description = "User ID reference")
    private Long userId;

    @Schema(description = "Conversation session ID")
    private String sessionId;

    @Schema(description = "Role: user/assistant/system")
    private String role;

    @Schema(description = "Message content")
    private String message;

    @Schema(description = "Created timestamp")
    private LocalDateTime createdAt;
}
