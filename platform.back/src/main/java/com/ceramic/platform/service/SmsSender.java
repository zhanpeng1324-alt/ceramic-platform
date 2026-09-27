package com.ceramic.platform.service;

/**
 * 短信发送通道抽象（策略模式扩展点）：
 * 演示/开发环境用 DemoSmsSender（验证码回显），生产环境可无缝替换为阿里云/腾讯云实现，业务代码零改动。
 */
public interface SmsSender {

    /** 是否演示模式：验证码不真实外发，回显给前端 */
    boolean demoMode();

    /** 发送验证码短信 */
    void send(String phone, String code);
}
