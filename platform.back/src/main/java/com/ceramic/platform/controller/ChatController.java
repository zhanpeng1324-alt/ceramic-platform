package com.ceramic.platform.controller;

import com.ceramic.platform.entity.ChatConversation;
import com.ceramic.platform.entity.ChatMessage;
import com.ceramic.platform.service.ChatService;
import com.ceramic.platform.dto.AiChatResponse;
import com.ceramic.platform.entity.User;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {
    private final ChatService chatService;

    @GetMapping("/conversations")
    public Map<String, Object> getConversations(@RequestParam(value = "customerId", required = false) Long customerId,
                                                HttpServletRequest request) {
        User currentUser = currentUser(request);
        if ("customer".equals(currentUser.getRole())) {
            customerId = currentUser.getId();
        }
        if (customerId == null) {
            throw new IllegalArgumentException("缺少消费者 ID");
        }
        List<ChatConversation> conversations = chatService.getConversations(customerId);
        return Map.of("code", 200, "msg", "success", "data", conversations);
    }

    @PostMapping("/conversations")
    public Map<String, Object> createConversation(@RequestBody(required = false) Map<String, Long> body,
                                                   HttpServletRequest request) {
        User currentUser = currentUser(request);
        Long customerId = "customer".equals(currentUser.getRole()) ? currentUser.getId() : body == null ? null : body.get("customerId");
        if (customerId == null) {
            throw new IllegalArgumentException("缺少消费者 ID");
        }
        ChatConversation conversation = chatService.createConversation(customerId);
        return Map.of("code", 200, "msg", "success", "data", conversation);
    }

    @GetMapping("/messages")
    public Map<String, Object> getMessages(@RequestParam("conversationId") Long conversationId, HttpServletRequest request) {
        User currentUser = currentUser(request);
        chatService.requireConversationAccess(conversationId, currentUser.getId(), currentUser.getRole());
        List<ChatMessage> messages = chatService.getMessages(conversationId);
        return Map.of("code", 200, "msg", "success", "data", messages);
    }

    @PostMapping("/messages")
    public Map<String, Object> sendMessage(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        User currentUser = currentUser(request);
        Long conversationId = Long.valueOf(body.get("conversationId").toString());
        chatService.requireConversationAccess(conversationId, currentUser.getId(), currentUser.getRole());
        Long senderId = currentUser.getId();
        String senderRole = currentUser.getRole();
        String message = body.get("message").toString();
        ChatMessage chatMessage = chatService.sendMessage(conversationId, senderId, senderRole, message);
        return Map.of("code", 200, "msg", "success", "data", chatMessage);
    }

    @PostMapping("/conversations/{conversationId}/read")
    public Map<String, Object> markAsRead(@PathVariable Long conversationId, HttpServletRequest request) {
        User currentUser = currentUser(request);
        chatService.requireConversationAccess(conversationId, currentUser.getId(), currentUser.getRole());
        chatService.markMessagesAsRead(conversationId);
        return Map.of("code", 200, "msg", "success");
    }

    @PostMapping("/conversations/{conversationId}/close")
    public Map<String, Object> closeConversation(@PathVariable Long conversationId, HttpServletRequest request) {
        User currentUser = currentUser(request);
        chatService.requireConversationAccess(conversationId, currentUser.getId(), currentUser.getRole());
        chatService.closeConversation(conversationId);
        return Map.of("code", 200, "msg", "success");
    }

    @GetMapping("/service/conversations")
    public Map<String, Object> getAllConversations(
            @RequestParam(value = "transferredOnly", required = false) Boolean transferredOnly) {
        List<ChatConversation> conversations = Boolean.TRUE.equals(transferredOnly)
                ? chatService.getTransferredConversations()
                : chatService.getAllConversations();
        return Map.of("code", 200, "msg", "success", "data", conversations);
    }

    @PutMapping("/service/conversations/{conversationId}/status")
    public Map<String, Object> updateStatus(@PathVariable Long conversationId, @RequestBody Map<String, String> body) {
        String status = body.get("status");
        chatService.updateConversationStatus(conversationId, status);
        return Map.of("code", 200, "msg", "success");
    }

    @PostMapping("/ai/messages")
    public Map<String, Object> sendAiMessage(@RequestBody Map<String, String> body, HttpServletRequest request) {
        User currentUser = currentUser(request);
        if (currentUser == null || !"customer".equals(currentUser.getRole())) {
            throw new IllegalArgumentException("仅消费者可使用智能客服");
        }
        Long conversationId = Long.valueOf(body.get("conversationId"));
        AiChatResponse response = chatService.sendAiMessage(conversationId, currentUser.getId(), body.get("message"));
        return Map.of("code", 200, "msg", "success", "data", response);
    }

    @PostMapping("/conversations/{conversationId}/transfer")
    public Map<String, Object> transferToHuman(@PathVariable Long conversationId, @RequestBody(required = false) Map<String, Object> body, HttpServletRequest request) {
        User currentUser = currentUser(request);
        chatService.requireConversationAccess(conversationId, currentUser.getId(), currentUser.getRole());
        Long agentId = null;
        if (body != null && body.get("agentId") != null) {
            agentId = Long.valueOf(body.get("agentId").toString());
        }
        chatService.transferToHuman(conversationId, agentId);
        return Map.of("code", 200, "msg", "success");
    }

    private User currentUser(HttpServletRequest request) {
        User user = (User) request.getAttribute("currentUser");
        if (user == null) {
            throw new IllegalArgumentException("未登录");
        }
        return user;
    }
}
