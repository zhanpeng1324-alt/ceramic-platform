package com.ceramic.platform.service;

import com.ceramic.platform.dto.AdminStatsDTO;
import com.ceramic.platform.mapper.AdminStatsMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 管理员数据看板统计服务。
 */
@Service
@RequiredArgsConstructor
public class AdminStatsService {

    private final AdminStatsMapper adminStatsMapper;

    public AdminStatsDTO getStats() {
        AdminStatsDTO dto = new AdminStatsDTO();
        // 近 7 天时间序列，补齐缺失日期为 0
        dto.setOrderCount7d(fill7d(adminStatsMapper.selectOrderCount7d()));
        dto.setSalesAmount7d(fill7d(adminStatsMapper.selectSalesAmount7d()));
        // 汇总计数
        dto.setTotalUsers(adminStatsMapper.countUsers());
        dto.setTotalProducts(adminStatsMapper.countProducts());
        dto.setPendingPayOrders(adminStatsMapper.countPendingPayOrders());
        dto.setPendingReturns(adminStatsMapper.countPendingReturns());
        dto.setPendingCustomizations(adminStatsMapper.countPendingCustomizations());
        dto.setLowStockProducts(adminStatsMapper.countLowStock());
        // 图表分布
        dto.setOrderStatusDistribution(adminStatsMapper.selectOrderStatusDistribution());
        dto.setTopProducts(adminStatsMapper.selectTopProducts());
        dto.setLowStockList(adminStatsMapper.selectLowStockProducts());
        return dto;
    }

    /**
     * 将数据库返回的稀疏日期点补齐为连续的近 7 天序列。
     */
    private List<AdminStatsDTO.DatePoint> fill7d(List<AdminStatsDTO.DatePoint> rows) {
        Map<String, BigDecimal> present = rows.stream()
                .collect(Collectors.toMap(AdminStatsDTO.DatePoint::getDate,
                        AdminStatsDTO.DatePoint::getValue, (a, b) -> a));
        List<AdminStatsDTO.DatePoint> result = new ArrayList<>();
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        LocalDate today = LocalDate.now();
        for (int i = 6; i >= 0; i--) {
            String date = today.minusDays(i).format(fmt);
            BigDecimal value = present.getOrDefault(date, BigDecimal.ZERO);
            result.add(new AdminStatsDTO.DatePoint(date, value));
        }
        return result;
    }
}
