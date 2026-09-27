package com.ceramic.platform.dto;

import com.ceramic.platform.entity.ChatMessage;

public record AiChatResponse(
        ChatMessage userMessage,
        ChatMessage assistantMessage,
        boolean transferredToHuman,
        boolean suggestTransfer
) {
}
