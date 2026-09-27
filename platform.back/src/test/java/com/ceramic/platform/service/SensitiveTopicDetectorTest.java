package com.ceramic.platform.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SensitiveTopicDetectorTest {
    private final SensitiveTopicDetector detector = new SensitiveTopicDetector();

    @Test
    void detectsOrderRelatedQuestions() {
        assertTrue(detector.requiresHuman("我的订单什么时候发货？"));
    }

    @Test
    void detectsLogisticsQuestions() {
        assertTrue(detector.requiresHuman("快递单号是多少？"));
    }

    @Test
    void detectsRefundQuestions() {
        assertTrue(detector.requiresHuman("退款什么时候到账"));
    }

    @Test
    void detectsComplaints() {
        assertTrue(detector.requiresHuman("我要投诉你们"));
    }

    @Test
    void allowsGeneralCeramicQuestions() {
        assertFalse(detector.requiresHuman("青釉和白釉有什么区别？"));
        assertFalse(detector.requiresHuman("如何保养陶瓷？"));
    }
}
