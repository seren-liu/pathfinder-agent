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
 * 用户聊天会话元数据
 */
@Getter
@Setter
@TableName("chat_sessions")
@Schema(name = "ChatSession", description = "Chat session metadata")
public class ChatSession implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.INPUT)
    @Schema(description = "Session ID")
    private String id;

    @Schema(description = "Owner user ID")
    private Long userId;

    @Schema(description = "Session title")
    private String title;

    @Schema(description = "Last message preview")
    private String lastMessage;

    @Schema(description = "Message count")
    private Integer messageCount;

    @Schema(description = "Created timestamp")
    private LocalDateTime createdAt;

    @Schema(description = "Last updated timestamp")
    private LocalDateTime updatedAt;
}

