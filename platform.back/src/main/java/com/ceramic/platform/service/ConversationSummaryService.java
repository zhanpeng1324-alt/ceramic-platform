package com.ceramic.platform.service;

import com.ceramic.platform.entity.ChatMessage;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class ConversationSummaryService {
    private static final int MAX_SUMMARY_LENGTH = 500;

    public String summarize(List<ChatMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return "用户请求转接人工客服，暂无咨询内容。";
        }

        List<String> customerMessages = messages.stream()
                .filter(m -> "customer".equals(m.getSenderRole()))
                .map(ChatMessage::getMessage)
                .filter(m -> m != null && !m.isBlank())
                .toList();

        if (customerMessages.isEmpty()) {
            return "用户请求转接人工客服，暂无文字咨询记录。";
        }

        String topics = customerMessages.stream()
                .limit(5)
                .collect(Collectors.joining("；"));

        String summary = "用户主要咨询：" + topics;
        if (customerMessages.size() > 5) {
            summary += "（共 " + customerMessages.size() + " 条用户消息）";
        }
        return truncate(summary);
    }

    private String truncate(String text) {
        if (text.length() <= MAX_SUMMARY_LENGTH) {
            return text;
        }
        return text.substring(0, MAX_SUMMARY_LENGTH - 3) + "...";
    }
}
