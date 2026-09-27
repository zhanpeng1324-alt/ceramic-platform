package com.ceramic.platform.dto;

import com.ceramic.platform.entity.Product;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * 管理员数据看板统计响应。
 */
@Data
public class AdminStatsDTO {

    /** 近 7 天每日订单量 */
    private List<DatePoint> orderCount7d;

    /** 近 7 天每日销售额（仅含已成交订单） */
    private List<DatePoint> salesAmount7d;

    /** 总用户数 */
    private long totalUsers;

    /** 总商品数 */
    private long totalProducts;

    /** 待支付订单数 */
    private long pendingPayOrders;

    /** 待处理售后数 */
    private long pendingReturns;

    /** 待审核定制数 */
    private long pendingCustomizations;

    /** 低库存商品数 */
    private long lowStockProducts;

    /** 订单状态占比分布 */
    private List<NameValue> orderStatusDistribution;

    /** 热销商品 Top 5 */
    private List<NameValue> topProducts;

    /** 低库存商品列表 */
    private List<Product> lowStockList;

    /** 时间序列数据点 */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DatePoint {
        /** 日期 yyyy-MM-dd */
        private String date;
        /** 数值（订单量为整数，销售额为金额） */
        private BigDecimal value;
    }

    /** 名称-数值对，用于饼图/排行柱状图 */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class NameValue {
        private String name;
        private long value;
    }
}
