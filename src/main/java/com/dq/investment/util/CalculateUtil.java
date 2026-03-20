package com.dq.investment.util;

import com.dq.investment.entity.ProfitRecord;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * 终极修复版：年化收益率计算工具（解决IRR异常/极值问题）
 */
public class CalculateUtil {
    // 精度（小数点后4位）
    private static final int SCALE = 4;
    // 一年天数
    private static final int DAYS_OF_YEAR = 365;
    // IRR迭代精度
    private static final BigDecimal IRR_PRECISION = new BigDecimal("0.000001");
    private static final int IRR_MAX_ITERATIONS = 100;
    // 年化收益率极值（避免-100%/超大正数）
    private static final BigDecimal MIN_ANNUALIZED = new BigDecimal("-99.99");
    private static final BigDecimal MAX_ANNUALIZED = new BigDecimal("1000.00");

    /**
     * 最终版：基于记录的年化收益率计算（兼容所有场景）
     * @param records 收益/申赎记录（transactionAmount：申购=正，赎回=负；profitAmount：收益=正）
     *                【关键修正】：transactionAmount 恢复为「申购=正，赎回=负」（符合业务直觉）
     * @return 年化收益率（%），异常场景返回合理值
     */
    public static BigDecimal calculateAnnualizedReturnByRecords(List<ProfitRecord> records) {
        // 场景1：无记录 → 返回0
        if (records == null || records.isEmpty()) {
            return BigDecimal.ZERO.setScale(SCALE, RoundingMode.HALF_UP);
        }

        // 步骤1：排序记录
        List<ProfitRecord> sortedRecords = new ArrayList<>(records);
        sortedRecords.sort(Comparator.comparing(ProfitRecord::getRecordDate));

        // 步骤2：提取核心数据（总成本、总收益、持有天数）
        BigDecimal totalCost = BigDecimal.ZERO;    // 总成本（申购总额，正）
        BigDecimal totalProfit = BigDecimal.ZERO;  // 总收益（正）
        LocalDate firstDate = sortedRecords.get(0).getRecordDate();
        LocalDate lastDate = sortedRecords.get(sortedRecords.size() - 1).getRecordDate();
        long holdDays = ChronoUnit.DAYS.between(firstDate, lastDate);

        // 步骤3：统计现金流（申购=正，赎回=负，收益=正）
        List<BigDecimal> cashFlows = new ArrayList<>();
        List<Integer> dayOffsets = new ArrayList<>();
        boolean hasCost = false;   // 是否有申购（成本）
        boolean hasProfit = false; // 是否有收益/赎回

        for (ProfitRecord record : sortedRecords) {
            LocalDate date = record.getRecordDate();
            int days = (int) ChronoUnit.DAYS.between(firstDate, date);

            // 申赎金额：申购=正，赎回=负（符合业务直觉）
            BigDecimal transAmount = record.getTransactionAmount() == null ? BigDecimal.ZERO : record.getTransactionAmount();
            // 收益金额：正
            BigDecimal profitAmount = record.getProfitAmount() == null ? BigDecimal.ZERO : record.getProfitAmount();

            // 统计总成本（申购总额，赎回抵扣成本）
            if (transAmount.compareTo(BigDecimal.ZERO) > 0) {
                totalCost = totalCost.add(transAmount);
                hasCost = true;
            } else if (transAmount.compareTo(BigDecimal.ZERO) < 0) {
                totalCost = totalCost.add(transAmount); // 赎回抵扣成本（负）
            }

            // 统计总收益
            if (profitAmount.compareTo(BigDecimal.ZERO) > 0) {
                totalProfit = totalProfit.add(profitAmount);
                hasProfit = true;
            }

            // 构造IRR现金流（IRR规则：支出=负，收入=正 → 申购=负，收益/赎回=正）
            BigDecimal irrFlow = transAmount.multiply(new BigDecimal(-1)).add(profitAmount);
            cashFlows.add(irrFlow);
            dayOffsets.add(days);

            if (irrFlow.compareTo(BigDecimal.ZERO) != 0) {
                if (irrFlow.compareTo(BigDecimal.ZERO) < 0) {
                    hasCost = true;
                } else {
                    hasProfit = true;
                }
            }
        }

        // 场景2：无成本/无持有天数 → 返回0
        if (!hasCost || holdDays <= 0 || totalCost.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO.setScale(SCALE, RoundingMode.HALF_UP);
        }

        // 场景3：有成本但无收益 → 返回0（避免IRR迭代出-100%）
        if (!hasProfit) {
            return BigDecimal.ZERO.setScale(SCALE, RoundingMode.HALF_UP);
        }

        // 场景4：单笔投资（只有一笔申购+一笔收益）→ 用简单复利计算（避免IRR迭代）
        if (cashFlows.size() == 2 && cashFlows.get(0).compareTo(BigDecimal.ZERO) < 0 && cashFlows.get(1).compareTo(BigDecimal.ZERO) > 0) {
            return calculateSimpleAnnualizedReturn(totalCost, totalProfit, holdDays);
        }

        // 场景5：多次申赎 → 用IRR计算（修复迭代逻辑）
        BigDecimal irr = calculateIRR(cashFlows, dayOffsets);
        // 修正IRR极值
        if (irr.abs().compareTo(new BigDecimal("100")) > 0) {
            return calculateSimpleAnnualizedReturn(totalCost, totalProfit, holdDays);
        }

        // 年化计算（修复小数次幂+极值）
        BigDecimal dailyIRR = irr.divide(new BigDecimal(100), SCALE + 2, RoundingMode.HALF_UP);
        double annualizedDouble = (Math.pow(1 + dailyIRR.doubleValue(), DAYS_OF_YEAR) - 1) * 100;
        BigDecimal annualized = BigDecimal.valueOf(annualizedDouble).setScale(SCALE, RoundingMode.HALF_UP);

        // 修正年化极值（避免-99.99%/1000%以上）
        if (annualized.compareTo(MIN_ANNUALIZED) < 0) {
            return MIN_ANNUALIZED;
        } else if (annualized.compareTo(MAX_ANNUALIZED) > 0) {
            return MAX_ANNUALIZED;
        } else {
            return annualized;
        }
    }

    /**
     * 简化版：单笔投资年化收益率（复利）
     * @param cost 总成本（申购金额，正）
     * @param profit 总收益（正）
     * @param holdDays 持有天数
     * @return 年化收益率（%）
     */
    public static BigDecimal calculateSimpleAnnualizedReturn(BigDecimal cost, BigDecimal profit, long holdDays) {
        if (cost.compareTo(BigDecimal.ZERO) == 0 || holdDays <= 0) {
            return BigDecimal.ZERO.setScale(SCALE, RoundingMode.HALF_UP);
        }
        // 总收益率 = 收益 / 成本
        BigDecimal totalReturn = profit.divide(cost, SCALE + 2, RoundingMode.HALF_UP);
        // 复利年化：(1+总收益率)^(365/持有天数) - 1
        double annualFactor = (double) DAYS_OF_YEAR / holdDays;
        double annualized = (Math.pow(1 + totalReturn.doubleValue(), annualFactor) - 1) * 100;
        // 修正极值
        BigDecimal result = BigDecimal.valueOf(annualized).setScale(SCALE, RoundingMode.HALF_UP);
        if (result.compareTo(MIN_ANNUALIZED) < 0) {
            return MIN_ANNUALIZED;
        }
        if (result.compareTo(MAX_ANNUALIZED) > 0) {
            return MAX_ANNUALIZED;
        }
        return result;
    }

    /**
     * 修复IRR计算（鲁棒性提升）
     */
    private static BigDecimal calculateIRR(List<BigDecimal> cashFlows, List<Integer> dayOffsets) {
        // 初始猜测值改为1%（更保守，避免发散）
        BigDecimal guess = new BigDecimal("1.0");
        BigDecimal irr = guess;

        for (int i = 0; i < IRR_MAX_ITERATIONS; i++) {
            BigDecimal npv = calculateNPV(irr, cashFlows, dayOffsets);
            // NPV接近0则退出
            if (npv.abs().compareTo(IRR_PRECISION) < 0) {
                break;
            }

            BigDecimal npvDerivative = calculateNPVDerivative(irr, cashFlows, dayOffsets);
            // 导数为0则退出
            if (npvDerivative.compareTo(BigDecimal.ZERO) == 0) {
                break;
            }

            // 牛顿迭代
            BigDecimal delta = npv.divide(npvDerivative, SCALE + 2, RoundingMode.HALF_UP);
            BigDecimal newIrr = irr.subtract(delta);

            // 迭代步长太小则退出
            if (delta.abs().compareTo(IRR_PRECISION) < 0) {
                irr = newIrr;
                break;
            }

            irr = newIrr;
            // 防止IRR发散到极端值
            if (irr.abs().compareTo(new BigDecimal("100")) > 0) {
                irr = BigDecimal.ZERO;
                break;
            }
        }

        return irr.setScale(SCALE, RoundingMode.HALF_UP);
    }

    /**
     * 修复NPV计算
     */
    private static BigDecimal calculateNPV(BigDecimal rate, List<BigDecimal> cashFlows, List<Integer> dayOffsets) {
        BigDecimal decimalRate = rate.divide(new BigDecimal(100), SCALE + 2, RoundingMode.HALF_UP);
        BigDecimal npv = BigDecimal.ZERO;

        for (int i = 0; i < cashFlows.size(); i++) {
            BigDecimal flow = cashFlows.get(i);
            int days = dayOffsets.get(i);

            if (days == 0) {
                npv = npv.add(flow);
                continue;
            }

            // 小数次幂用Math.pow计算
            double exponent = (double) days / DAYS_OF_YEAR;
            double discountFactor = 1.0 / Math.pow(1.0 + decimalRate.doubleValue(), exponent);
            npv = npv.add(flow.multiply(BigDecimal.valueOf(discountFactor))
                    .setScale(SCALE + 2, RoundingMode.HALF_UP));
        }

        return npv;
    }

    /**
     * 修复NPV导数计算
     */
    private static BigDecimal calculateNPVDerivative(BigDecimal rate, List<BigDecimal> cashFlows, List<Integer> dayOffsets) {
        BigDecimal decimalRate = rate.divide(new BigDecimal(100), SCALE + 2, RoundingMode.HALF_UP);
        BigDecimal derivative = BigDecimal.ZERO;

        for (int i = 0; i < cashFlows.size(); i++) {
            BigDecimal flow = cashFlows.get(i);
            int days = dayOffsets.get(i);

            if (days == 0) {
                continue;
            }

            double exponent = (double) days / DAYS_OF_YEAR;
            double discountFactor = 1.0 / Math.pow(1.0 + decimalRate.doubleValue(), exponent + 1.0);
            double derivativeTerm = -1.0 * exponent * flow.doubleValue() * discountFactor;
            derivative = derivative.add(BigDecimal.valueOf(derivativeTerm)
                    .setScale(SCALE + 2, RoundingMode.HALF_UP));
        }

        return derivative.multiply(new BigDecimal(100));
    }

    // 无风险收益率（年化，默认国债利率3%）
    private static final BigDecimal RISK_FREE_RATE = new BigDecimal("3.00");

    /**
     * 计算最大回撤(%)
     * @param records 该产品的所有收益记录（按日期排序）
     * @return 最大回撤(%)（负数表示回撤，取绝对值）
     */
    public static BigDecimal calculateMaxDrawdown(List<ProfitRecord> records) {
        if (records.isEmpty()) {
            return BigDecimal.ZERO;
        }

        // 按日期排序
        // 关键修复：将不可变列表转为可变ArrayList后再排序
        List<ProfitRecord> mutableRecords = new ArrayList<>(records);
        mutableRecords.sort(Comparator.comparing(ProfitRecord::getRecordDate)); // 改用可变列表排序
        BigDecimal maxNav = BigDecimal.ZERO; // 最高净值
        BigDecimal maxDrawdown = BigDecimal.ZERO; // 最大回撤

        BigDecimal currentNav = BigDecimal.ZERO; // 当前净值（本金+累计收益）
        BigDecimal totalPrincipal = BigDecimal.ZERO; // 累计本金

        for (ProfitRecord record : mutableRecords) {
            // 累计本金（申赎）
            totalPrincipal = totalPrincipal.add(record.getTransactionAmount() == null ? BigDecimal.ZERO : record.getTransactionAmount());
            // 当前净值 = 累计本金 + 累计收益
            currentNav = totalPrincipal.add(record.getProfitAmount() == null ? BigDecimal.ZERO : record.getProfitAmount());

            // 更新最高净值
            if (currentNav.compareTo(maxNav) > 0) {
                maxNav = currentNav;
            }

            // 计算回撤率 = (当前净值 - 最高净值) / 最高净值 * 100
            if (maxNav.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal drawdown = currentNav.subtract(maxNav)
                        .divide(maxNav, 4, RoundingMode.HALF_UP)
                        .multiply(new BigDecimal(100));
                // 取最大回撤（最负数）
                if (drawdown.compareTo(maxDrawdown) < 0) {
                    maxDrawdown = drawdown;
                }
            }
        }

        // 返回绝对值（保留4位小数）
        return maxDrawdown.abs().setScale(4, RoundingMode.HALF_UP);
    }

    /**
     * 计算夏普比率
     * 夏普比率 = (年化收益率 - 无风险收益率) / 收益波动率
     * @param annualizedReturn 年化收益率(%)
     * @param records 收益记录（计算波动率）
     * @return 夏普比率
     */
    public static BigDecimal calculateSharpeRatio(BigDecimal annualizedReturn, List<ProfitRecord> records) {
        if (records.size() < 2 || annualizedReturn.compareTo(RISK_FREE_RATE) <= 0) {
            return BigDecimal.ZERO;
        }

        // 计算收益波动率（标准差）
        BigDecimal avgReturn = calculateAverageReturn(records);
        BigDecimal variance = BigDecimal.ZERO; // 方差

        for (ProfitRecord record : records) {
            BigDecimal dailyReturn = record.getProfitRate() == null ? BigDecimal.ZERO : record.getProfitRate();
            BigDecimal diff = dailyReturn.subtract(avgReturn);
            variance = variance.add(diff.multiply(diff));
        }

        // 标准差 = 根号(方差/(n-1))
        BigDecimal stdDev = BigDecimal.valueOf(Math.sqrt(variance.divide(new BigDecimal(records.size() - 1), 4, RoundingMode.HALF_UP).doubleValue()));
        if (stdDev.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }

        // 夏普比率 = (年化收益率 - 无风险收益率) / 波动率
        BigDecimal sharpe = annualizedReturn.subtract(RISK_FREE_RATE)
                .divide(stdDev, 4, RoundingMode.HALF_UP);

        return sharpe.setScale(4, RoundingMode.HALF_UP);
    }

    /**
     * 计算日均收益率（用于波动率）
     */
    private static BigDecimal calculateAverageReturn(List<ProfitRecord> records) {
        BigDecimal totalReturn = BigDecimal.ZERO;
        for (ProfitRecord record : records) {
            totalReturn = totalReturn.add(record.getProfitRate() == null ? BigDecimal.ZERO : record.getProfitRate());
        }
        return totalReturn.divide(new BigDecimal(records.size()), 4, RoundingMode.HALF_UP);
    }

    /**
     * 手动计算单条记录的指标（更新该记录的年化/最大回撤/夏普比率）
     * @param productId 产品ID
     * @param recordId  要计算的记录ID
     * @param allRecords 该产品的所有记录
     * @return 更新后的单条记录
     */
    public static ProfitRecord calculateSingleRecord(Long productId, Long recordId, List<ProfitRecord> allRecords) {
        // 筛选出该记录之前的所有数据（含当前记录）
        ProfitRecord targetRecord = null;
        for (ProfitRecord record : allRecords) {
            if (record.getId().equals(recordId)) {
                targetRecord = record;
                break;
            }
        }
        if (targetRecord == null) {
            return null;
        }

        // 按日期筛选到当前记录的所有数据
        LocalDate targetDate = targetRecord.getRecordDate();
        List<ProfitRecord> recordsToTarget = allRecords.stream()
                .filter(r -> r.getRecordDate().isBefore(targetDate) || r.getRecordDate().isEqual(targetDate))
                .toList();

        // 计算指标
        BigDecimal annualized = calculateAnnualizedReturnByRecords(recordsToTarget);
        BigDecimal maxDrawdown = calculateMaxDrawdown(recordsToTarget);
        BigDecimal sharpe = calculateSharpeRatio(annualized, recordsToTarget);

        // 更新当前记录
        targetRecord.setAnnualizedReturn(annualized);
        targetRecord.setMaxDrawdown(maxDrawdown);
        targetRecord.setSharpeRatio(sharpe);

        return targetRecord;
    }
}