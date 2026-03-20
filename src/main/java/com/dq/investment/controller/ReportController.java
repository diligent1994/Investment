package com.dq.investment.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dq.investment.entity.ProfitRecord;
import com.dq.investment.entity.Product;
import com.dq.investment.service.ProfitRecordService;
import com.dq.investment.service.ProductService;
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
    private final ProfitRecordService incomeService;

    /**
     * 获取所有产品的收益对比（简单聚合）
     */
    @GetMapping("/product-comparison")
    public List<Map<String, Object>> productComparison() {
        List<Product> products = productService.list();
        List<Map<String, Object>> result = new ArrayList<>();
        for (Product p : products) {
            LambdaQueryWrapper<ProfitRecord> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(ProfitRecord::getProductId, p.getId());
            List<ProfitRecord> incomes = incomeService.list(wrapper);
            BigDecimal totalIncome = incomes.stream()
                    .map(ProfitRecord::getProfitAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            Map<String, Object> item = new HashMap<>();
            item.put("productId", p.getId());
            item.put("productName", p.getName());
            item.put("totalIncome", totalIncome);
            item.put("purchaseAmount", p.getInvestAmount());
            result.add(item);
        }
        return result;
    }

    /**
     * 获取某个产品的收益曲线（按日期分组）
     */
    @GetMapping("/income-trend")
    public List<Map<String, Object>> incomeTrend(@RequestParam Long productId,
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
        List<ProfitRecord> incomes = incomeService.list(wrapper);
        return incomes.stream().map(r -> {
            Map<String, Object> m = new HashMap<>();
            m.put("date", r.getRecordDate());
            m.put("income", r.getProfitAmount());
            return m;
        }).collect(Collectors.toList());
    }
}