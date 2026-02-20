package com.travel.agent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 会话消息明细
 */
@Getter
@Setter
@TableName("chat_session_messages")
@Schema(name = "ChatSessionMessage", description = "Chat session message")
public class ChatSessionMessage implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    @Schema(description = "Message ID")
    private Long id;

    @Schema(description = "Session ID")
    private String sessionId;

    @Schema(description = "User ID")
    private Long userId;

    @Schema(description = "Role: user/assistant/system")
    private String role;

    @Schema(description = "Plain text content")
    private String content;

    @Schema(description = "Created timestamp")
    private LocalDateTime createdAt;
}

