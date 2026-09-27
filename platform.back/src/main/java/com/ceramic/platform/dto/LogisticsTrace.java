package com.ceramic.platform.dto;

import lombok.Data;

/** 物流轨迹节点（当前由模拟物流网关生成，未来可由真实承运商 API 填充，结构不变）。 */
@Data
public class LogisticsTrace {
    private String time;
    private String status;
    private String description;

    public LogisticsTrace() {}

    public LogisticsTrace(String time, String status, String description) {
        this.time = time;
        this.status = status;
        this.description = description;
    }
}
