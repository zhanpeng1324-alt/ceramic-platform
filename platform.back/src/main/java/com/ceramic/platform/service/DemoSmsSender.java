package com.ceramic.platform.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 演示环境短信实现：不真实外发，仅记录日志。
 * 生产环境替换为阿里云/腾讯云 SmsSender 实现类即可，无需改动其他代码。
 *
 * 安全约束：demo-mode=true 时验证码回显给前端（仅限本地开发）；
 * 生产 profile 必须 sms.demo-mode=false，此时未接入真实通道会直接抛错拒绝发码，杜绝任意账号登录。
 */
@Component
public class DemoSmsSender implements SmsSender {

    private static final Logger log = LoggerFactory.getLogger(DemoSmsSender.class);

    @Value("${sms.demo-mode:true}")
    private boolean demoMode;

    @Override
    public boolean demoMode() {
        return demoMode;
    }

    @Override
    public void send(String phone, String code) {
        if (!demoMode) {
            throw new IllegalStateException("未接入真实短信通道：请实现 SmsSender 生产实现类，开发环境请将 sms.demo-mode 设为 true");
        }
        log.info("[SMS-DEMO] 向 {} 发送验证码：{}（5 分钟内有效）", phone, code);
    }
}
