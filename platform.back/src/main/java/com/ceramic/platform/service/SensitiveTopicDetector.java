package com.ceramic.platform.service;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Pattern;

@Component
public class SensitiveTopicDetector {
    private static final List<Pattern> PATTERNS = List.of(
            Pattern.compile("订单|单号|下单|付款|支付|发货|收货"),
            Pattern.compile("物流|快递|运单|配送|到货|签收|包裹|追踪|跟踪"),
            Pattern.compile("退款|退钱|退货|换货|售后进度|到账"),
            Pattern.compile("投诉|差评|举报|赔偿|起诉|工商|12315|不满|生气|欺诈|骗子")
    );

    public static final String HUMAN_TRANSFER_HINT = """
            您咨询的内容涉及订单、物流、退款或投诉等需要实时核实的信息，AI 无法查询具体进度或处理结果。
            请点击页面上的「转人工客服」按钮，我们的客服人员将为您跟进处理。""";

    public boolean requiresHuman(String message) {
        if (message == null || message.isBlank()) {
            return false;
        }
        String normalized = message.trim();
        return PATTERNS.stream().anyMatch(p -> p.matcher(normalized).find());
    }
}
