package com.ceramic.platform.service;

import com.ceramic.platform.dto.AiChatResponse;
import com.ceramic.platform.entity.ChatConversation;
import com.ceramic.platform.entity.ChatMessage;
import com.ceramic.platform.mapper.ChatMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ChatService {
    private final ChatMapper chatMapper;
    private final ChatUnreadCache chatUnreadCache;
    private final DeepSeekChatService deepSeekChatService;
    private final SensitiveTopicDetector sensitiveTopicDetector;
    private final AiRateLimitService aiRateLimitService;
    private final ConversationSummaryService conversationSummaryService;
    private final RealtimePushService realtimePushService;

    @Value("${deepseek.max-message-length:2000}")
    private int maxMessageLength;

    public List<ChatConversation> getConversations(Long customerId) {
        return chatMapper.findConversationsByCustomerId(customerId);
    }

    public List<ChatMessage> getMessages(Long conversationId) {
        return chatMapper.findMessagesByConversationId(conversationId);
    }

    @Transactional
    public ChatConversation createConversation(Long customerId) {
        ChatConversation conversation = new ChatConversation();
        conversation.setCustomerId(customerId);
        conversation.setStatus("open");
        conversation.setSubject("新对话");
        conversation.setPriority("medium");
        conversation.setUnreadCount(0);
        conversation.setTransferredToHuman(false);
        chatMapper.insertConversation(conversation);
        return conversation;
    }

    @Transactional
    public ChatMessage sendMessage(Long conversationId, Long senderId, String senderRole, String message) {
        validateMessageLength(message);
        ChatMessage chatMessage = new ChatMessage();
        chatMessage.setConversationId(conversationId);
        chatMessage.setSenderId(senderId);
        chatMessage.setSenderRole(senderRole);
        chatMessage.setMessage(message.trim());
        chatMessage.setType("text");
        chatMessage.setTimestamp(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        chatMessage.setReadBySupport("support".equals(senderRole) || "service".equals(senderRole));
        chatMessage.setReadByCustomer("customer".equals(senderRole));
        chatMapper.insertMessage(chatMessage);
        // 未读数语义：以「客服待办」为准——顾客发言 +1(待回复)，客服/AI/系统回复则清零(已回复)。
        // 转人工后 AI 不再自动回复，顾客留言会停留在「待回复」，正好提示人工介入。
        if ("customer".equals(senderRole)) {
            chatMapper.increaseUnreadOnNewMessage(conversationId, message.trim());
            chatUnreadCache.increment(conversationId);
        } else {
            chatMapper.resetUnreadOnReply(conversationId, message.trim());
            chatUnreadCache.reset(conversationId);
        }
        // 实时推送：会话双方订阅同一 topic；顾客发的消息额外提醒所有在线客服刷新列表
        realtimePushService.pushChatMessage(conversationId, chatMessage);
        if ("customer".equals(senderRole)) {
            realtimePushService.pushServiceInbox(java.util.Map.of(
                    "type", "message", "conversationId", conversationId));
        }
        return chatMessage;
    }

    public void markMessagesAsRead(Long conversationId) {
        chatMapper.markMessagesAsReadByCustomer(conversationId);
    }

    public void closeConversation(Long conversationId) {
        chatMapper.closeConversation(conversationId);
    }

    public List<ChatConversation> getAllConversations() {
        List<ChatConversation> conversations = chatMapper.findAllConversations();
        // 客服收件箱的待办数走 Redis 读路径，未命中回源 MySQL（dbFallback）并回填
        for (ChatConversation c : conversations) {
            long dbValue = c.getUnreadCount() == null ? 0L : c.getUnreadCount();
            c.setUnreadCount((int) chatUnreadCache.get(c.getId(), dbValue));
        }
        return conversations;
    }

    public List<ChatConversation> getTransferredConversations() {
        return chatMapper.findTransferredConversations();
    }

    public void updateConversationStatus(Long conversationId, String status) {
        chatMapper.updateConversationStatus(conversationId, status);
    }

    @Transactional
    public AiChatResponse sendAiMessage(Long conversationId, Long customerId, String message) {
        ChatConversation conversation = chatMapper.findConversationById(conversationId);
        if (conversation == null || !customerId.equals(conversation.getCustomerId())) {
            throw new IllegalArgumentException("对话不存在或无权访问");
        }
        validateMessageLength(message);

        aiRateLimitService.checkRateLimit(customerId);

        ChatMessage userMessage = sendMessage(conversationId, customerId, "customer", message.trim());

        boolean alreadyTransferred = Boolean.TRUE.equals(conversation.getTransferredToHuman());
        if (alreadyTransferred) {
            return new AiChatResponse(userMessage, null, true, false);
        }

        String trimmed = message.trim();
        if (sensitiveTopicDetector.requiresHuman(trimmed)) {
            ChatMessage assistantMessage = sendMessage(
                    conversationId, 0L, "ai", SensitiveTopicDetector.HUMAN_TRANSFER_HINT.trim());
            return new AiChatResponse(userMessage, assistantMessage, false, true);
        }

        List<ChatMessage> history = chatMapper.findMessagesByConversationId(conversationId);
        String answer = deepSeekChatService.reply(history);
        ChatMessage assistantMessage = sendMessage(conversationId, 0L, "ai", answer);
        return new AiChatResponse(userMessage, assistantMessage, false, false);
    }

    @Transactional
    public void transferToHuman(Long conversationId, Long agentId) {
        ChatConversation conversation = chatMapper.findConversationById(conversationId);
        if (conversation == null) {
            throw new IllegalArgumentException("对话不存在");
        }
        Long assignedAgentId = agentId != null ? agentId : 999L;

        List<ChatMessage> messages = chatMapper.findMessagesByConversationId(conversationId);
        String summary = conversationSummaryService.summarize(messages);
        chatMapper.transferToHuman(conversationId, assignedAgentId, summary);

        ChatMessage systemMessage = new ChatMessage();
        systemMessage.setConversationId(conversationId);
        systemMessage.setSenderId(0L);
        systemMessage.setSenderRole("system");
        systemMessage.setMessage("已为您转接人工客服，会话已标记为待人工处理，请稍候…");
        systemMessage.setType("system");
        systemMessage.setTimestamp(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        systemMessage.setReadBySupport(false);
        systemMessage.setReadByCustomer(true);
        chatMapper.insertMessage(systemMessage);
        chatMapper.resetUnreadOnReply(conversationId, "已转接人工客服");
        chatUnreadCache.reset(conversationId);
        // 实时推送：系统提示进会话房间；同时提醒客服端有新的转人工会话
        realtimePushService.pushChatMessage(conversationId, systemMessage);
        realtimePushService.pushServiceInbox(java.util.Map.of(
                "type", "transfer", "conversationId", conversationId));
    }

    public void requireConversationAccess(Long conversationId, Long userId, String role) {
        ChatConversation conversation = chatMapper.findConversationById(conversationId);
        if (conversation == null) {
            throw new IllegalArgumentException("对话不存在");
        }
        if ("customer".equals(role) && !userId.equals(conversation.getCustomerId())) {
            throw new IllegalArgumentException("无权访问此对话");
        }
    }

    private void validateMessageLength(String message) {
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("消息不能为空");
        }
        if (message.length() > maxMessageLength) {
            throw new IllegalArgumentException("消息长度不能超过 " + maxMessageLength + " 个字符");
        }
    }
}
