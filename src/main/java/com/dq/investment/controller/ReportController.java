package com.dq.investment.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dq.investment.common.Result;
import com.dq.investment.entity.ProfitRecord;
import com.dq.investment.entity.Product;
import com.dq.investment.service.ProfitRecordService;
import com.dq.investment.service.ProductService;
import com.dq.investment.util.CalculateUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ProductService productService;
    private final ProfitRecordService profitRecordService;

    /**
     * 多指标产品对比（年化收益率/最大回撤/夏普比率/费率）
     */
    @GetMapping("/product-comparison")
    public Result<List<Map<String, Object>>> productComparison() {
        List<Product> products = productService.list();
        List<Map<String, Object>> result = new ArrayList<>();

        for (Product p : products) {
            LambdaQueryWrapper<ProfitRecord> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(ProfitRecord::getProductId, p.getId());
            List<ProfitRecord> records = profitRecordService.list(wrapper);

            // 累计收益
            BigDecimal totalIncome = records.stream()
                    .map(r -> r.getProfitAmount() == null ? BigDecimal.ZERO : r.getProfitAmount())
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            // 多指标封装
            Map<String, Object> item = new HashMap<>();
            item.put("productId", p.getId());
            item.put("productName", p.getName());
            item.put("totalIncome", totalIncome);
            item.put("purchaseAmount", p.getInvestAmount());
            item.put("annualizedReturn", p.getAnnualizedReturn() == null ? BigDecimal.ZERO : p.getAnnualizedReturn());
            item.put("maxDrawdown", p.getMaxDrawdown() == null ? BigDecimal.ZERO : p.getMaxDrawdown());
            item.put("sharpeRatio", p.getSharpeRatio() == null ? BigDecimal.ZERO : p.getSharpeRatio());
            item.put("feeRate", p.getFeeRate() == null ? BigDecimal.ZERO : p.getFeeRate());
            item.put("liquidity", p.getLiquidity());

            result.add(item);
        }
        return Result.success(result);
    }

    /**
     * 产品多指标趋势（收益+年化+最大回撤）
     */
    @GetMapping("/multi-indicator-trend")
    public Result<List<Map<String, Object>>> multiIndicatorTrend(
            @RequestParam Long productId,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {

        LambdaQueryWrapper<ProfitRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ProfitRecord::getProductId, productId);
        if (startDate != null) {
            wrapper.ge(ProfitRecord::getRecordDate, LocalDate.parse(startDate));
        }
        if (endDate != null) {
            wrapper.le(ProfitRecord::getRecordDate, LocalDate.parse(endDate));
        }
        wrapper.orderByAsc(ProfitRecord::getRecordDate);

        List<ProfitRecord> records = profitRecordService.list(wrapper);
        List<Map<String, Object>> result = records.stream().map(r -> {
            Map<String, Object> m = new HashMap<>();
            m.put("date", r.getRecordDate());
            m.put("income", r.getProfitAmount());
            m.put("annualizedReturn", r.getAnnualizedReturn());
            m.put("maxDrawdown", r.getMaxDrawdown());
            m.put("sharpeRatio", r.getSharpeRatio());
            return m;
        }).collect(Collectors.toList());
        return Result.success(result);
    }

    // 原有收益趋势接口保留
    @GetMapping("/income-trend")
    public Result<List<Map<String, Object>>> incomeTrend(@RequestParam Long productId,
                                                         @RequestParam(required = false) String startDate,
                                                         @RequestParam(required = false) String endDate) {
        LambdaQueryWrapper<ProfitRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ProfitRecord::getProductId, productId);
        if (startDate != null) {
            wrapper.ge(ProfitRecord::getRecordDate, LocalDate.parse(startDate));
        }
        if (endDate != null) {
            wrapper.le(ProfitRecord::getRecordDate, LocalDate.parse(endDate));
        }
        wrapper.orderByAsc(ProfitRecord::getRecordDate);
        List<ProfitRecord> incomes = profitRecordService.list(wrapper);
        List<Map<String, Object>> result = incomes.stream().map(r -> {
            Map<String, Object> m = new HashMap<>();
            m.put("date", r.getRecordDate());
            m.put("income", r.getProfitAmount());
            return m;
        }).collect(Collectors.toList());
        return Result.success(result);
    }
}