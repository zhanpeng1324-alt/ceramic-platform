package com.ceramic.platform.service;

import com.ceramic.platform.entity.ChatMessage;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class DeepSeekChatService {
    private static final Logger log = LoggerFactory.getLogger(DeepSeekChatService.class);

    private static final String SYSTEM_PROMPT_PREFIX = """
            你是陶瓷电商与定制平台的智能客服「AI 陶瓷顾问」。用简洁、礼貌的中文回答。
            请严格依据下方【陶瓷知识库】回答材质、釉色、保养、定制流程、售后政策与常见问题。
            你可以回答：陶瓷商品介绍、材质解读、釉色选择、保养方法、定制流程说明、售后政策介绍等。
            你不能编造：订单具体状态、库存、实时价格、物流单号、退款进度、优惠活动、投诉处理结果。
            当用户询问需要实时查询的信息（订单状态、物流进度、退款进度、投诉处理）或表达不满情绪时，
            必须明确告知无法查询，并引导用户点击「转人工客服」按钮，由人工客服跟进。
            不要索要密码、验证码、身份证、银行卡等敏感信息。

            【陶瓷知识库】
            """;

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String apiKey;
    private final String model;
    private final int maxHistoryMessages;
    private final int maxTokens;

    public DeepSeekChatService(RestClient.Builder builder, ObjectMapper objectMapper,
                               @Value("${deepseek.base-url}") String baseUrl,
                               @Value("${deepseek.api-key:}") String apiKey,
                               @Value("${deepseek.model:deepseek-chat}") String model,
                               @Value("${deepseek.timeout-seconds:30}") int timeoutSeconds,
                               @Value("${deepseek.max-history-messages:12}") int maxHistoryMessages,
                               @Value("${deepseek.max-tokens:500}") int maxTokens) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(Math.max(5, timeoutSeconds)));
        requestFactory.setReadTimeout(Duration.ofSeconds(Math.max(5, timeoutSeconds)));

        this.restClient = builder.baseUrl(baseUrl).requestFactory(requestFactory).build();
        this.objectMapper = objectMapper;
        this.apiKey = apiKey;
        this.model = model;
        this.maxHistoryMessages = Math.max(1, maxHistoryMessages);
        this.maxTokens = Math.max(100, maxTokens);
    }

    public String reply(List<ChatMessage> history) {
        if (!StringUtils.hasText(apiKey)) {
            return fallbackNoApiKey();
        }

        List<Map<String, String>> messages = buildMessages(history);
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("model", model);
        request.put("messages", messages);
        request.put("temperature", 0.4);
        request.put("max_tokens", maxTokens);

        try {
            String body = restClient.post().uri("/chat/completions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer " + apiKey)
                    .body(request)
                    .retrieve()
                    .body(String.class);

            JsonNode content = objectMapper.readTree(body)
                    .path("choices").path(0).path("message").path("content");
            if (content.isTextual() && StringUtils.hasText(content.asText())) {
                return content.asText().trim();
            }
            log.warn("DeepSeek returned empty content");
        } catch (RestClientException ex) {
            log.warn("DeepSeek request failed: {}", ex.getMessage());
        } catch (Exception ex) {
            log.warn("DeepSeek response parse failed: {}", ex.getMessage());
        }
        return fallbackUnavailable();
    }

    private List<Map<String, String>> buildMessages(List<ChatMessage> history) {
        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", SYSTEM_PROMPT_PREFIX + CeramicKnowledgeBase.CONTENT));

        if (history != null) {
            history.stream()
                    .skip(Math.max(0, history.size() - maxHistoryMessages))
                    .forEach(message -> {
                        String role = "customer".equals(message.getSenderRole()) ? "user" : "assistant";
                        messages.add(Map.of("role", role, "content", message.getMessage()));
                    });
        }
        return messages;
    }

    public static String fallbackNoApiKey() {
        return "智能客服尚未配置服务密钥，已为您保留咨询内容，请点击「转人工客服」继续处理。";
    }

    public static String fallbackUnavailable() {
        return "暂时无法连接智能客服。您的问题已记录，请点击「转人工客服」或稍后再试。";
    }
}
