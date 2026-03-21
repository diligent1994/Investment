package com.dq.investment.util;

import com.dq.investment.entity.Product;
import com.dq.investment.entity.ProfitRecord;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * 理财产品收益计算工具类
 * 计算逻辑说明：
 * 1. 日IRR：采用牛顿迭代法求解现金流净现值为0的日内部收益率
 * 2. 年化收益率：日IRR * 365（按自然年折算）
 * 3. 夏普比率：需补充无风险利率、历史收益率标准差
 * 4. 最大回撤：需产品完整的市值时间序列（当前提示缺少字段）
 */
@Slf4j
public class CalculateUtil {
    // 迭代精度（IRR计算收敛阈值）
    private static final BigDecimal IRR_PRECISION = new BigDecimal("0.00000001");
    // 最大迭代次数
    private static final int MAX_ITERATION = 1000;
    // 无风险利率（年化，默认国债利率3%，可配置）
    private static final BigDecimal RISK_FREE_RATE = new BigDecimal("0.03");
    // 百分比转换系数
    private static final BigDecimal PERCENT = new BigDecimal("100");
    // 年化天数（自然年）
    private static final int DAYS_PER_YEAR = 365;

    private CalculateUtil() {
        // 工具类禁止实例化
    }

    /**
     * 字段校验：检查计算所需核心字段是否为空
     *
     * @param records 产品收益记录列表
     * @throws IllegalArgumentException 字段缺失时抛出异常
     */
    public static void validateProfitRecordFields(List<ProfitRecord> records) {
        if (records == null || records.isEmpty()) {
            throw new IllegalArgumentException("收益记录列表不能为空");
        }
        for (ProfitRecord record : records) {
            if (record.getRecordDate() == null) {
                throw new IllegalArgumentException("收益记录的recordDate（日期）字段不能为空");
            }
            if (record.getTransactionAmount() == null) {
                throw new IllegalArgumentException("收益记录的transactionAmount（申赎金额）字段不能为空");
            }
            if (record.getTotalAmount() == null) {
                throw new IllegalArgumentException("收益记录的totalAmount（持仓总市值）字段不能为空");
            }
            // 转换BigDecimal防止空指针
            record.setTransactionAmount(record.getTransactionAmount().setScale(6, RoundingMode.HALF_UP));
            record.setTotalAmount(record.getTotalAmount().setScale(6, RoundingMode.HALF_UP));
        }
    }

    /**
     * 按记录日期排序（升序）
     *
     * @param records 收益记录列表
     * @return 排序后的列表
     */
    public static List<ProfitRecord> sortRecordsByDate(List<ProfitRecord> records) {
        validateProfitRecordFields(records);
        List<ProfitRecord> sortedRecords = new ArrayList<>(records);
        Collections.sort(sortedRecords, Comparator.comparing(ProfitRecord::getRecordDate));
        return sortedRecords;
    }

    /**
     * 计算日IRR（内部收益率）
     *
     * @param records 产品收益记录列表（需按日期排序）
     * @return 日IRR（小数形式，如0.0001122表示日IRR为0.01122%）
     */
    public static BigDecimal calculateDailyIRR(List<ProfitRecord> records) {
        validateProfitRecordFields(records);
        List<ProfitRecord> sortedRecords = sortRecordsByDate(records);
        LocalDate firstDate = sortedRecords.get(0).getRecordDate();

        // 构建现金流和时间间隔（天数）
        List<BigDecimal> cashFlows = new ArrayList<>();
        List<Integer> daysList = new ArrayList<>();

        for (ProfitRecord record : sortedRecords) {
            long days = ChronoUnit.DAYS.between(firstDate, record.getRecordDate());
            daysList.add((int) days);
            // 现金流规则：申购（支出）为负，赎回/市值（收入）为正
            BigDecimal cashFlow = record.getTransactionAmount().negate();
            cashFlows.add(cashFlow);
        }
        // 最后一条记录的总市值作为最终现金流（收入）
        ProfitRecord lastRecord = sortedRecords.get(sortedRecords.size() - 1);
        cashFlows.set(cashFlows.size() - 1, cashFlows.get(cashFlows.size() - 1).add(lastRecord.getTotalAmount()));

        // 牛顿迭代法求解IRR
        BigDecimal guess = new BigDecimal("0.0001"); // 初始猜测值
        BigDecimal irr = guess;
        for (int i = 0; i < MAX_ITERATION; i++) {
            BigDecimal npv = calculateNPV(irr, cashFlows, daysList);
            BigDecimal npvDerivative = calculateNPVDerivative(irr, cashFlows, daysList);

            if (npvDerivative.abs().compareTo(IRR_PRECISION) < 0) {
                break; // 导数接近0，停止迭代
            }

            BigDecimal newIrr = irr.subtract(npv.divide(npvDerivative, 10, RoundingMode.HALF_UP));
            if (newIrr.subtract(irr).abs().compareTo(IRR_PRECISION) < 0) {
                irr = newIrr;
                break; // 收敛，停止迭代
            }
            irr = newIrr;
        }
        return irr.multiply(PERCENT).setScale(8, RoundingMode.HALF_UP);
    }

    /**
     * 计算净现值（NPV）
     */
    private static BigDecimal calculateNPV(BigDecimal rate, List<BigDecimal> cashFlows, List<Integer> daysList) {
        BigDecimal npv = BigDecimal.ZERO;
        for (int i = 0; i < cashFlows.size(); i++) {
            BigDecimal dayFactor = new BigDecimal(daysList.get(i));
            // NPV = Σ CFt / (1 + r)^t （t为天数）
            BigDecimal denominator = BigDecimal.ONE.add(rate).pow(dayFactor.intValue());
            npv = npv.add(cashFlows.get(i).divide(denominator, 10, RoundingMode.HALF_UP));
        }
        return npv;
    }

    /**
     * 计算NPV的导数（用于牛顿迭代）
     */
    private static BigDecimal calculateNPVDerivative(BigDecimal rate, List<BigDecimal> cashFlows, List<Integer> daysList) {
        BigDecimal derivative = BigDecimal.ZERO;
        for (int i = 0; i < cashFlows.size(); i++) {
            BigDecimal dayFactor = new BigDecimal(daysList.get(i));
            BigDecimal denominator = BigDecimal.ONE.add(rate).pow(dayFactor.intValue() + 1);
            BigDecimal term = cashFlows.get(i).multiply(dayFactor).negate().divide(denominator, 10, RoundingMode.HALF_UP);
            derivative = derivative.add(term);
        }
        return derivative;
    }

    /**
     * 计算年化收益率
     *
     * @param dailyIRR 日IRR（小数形式）
     * @return 年化收益率（小数形式，如0.04178表示4.178%）
     */
    public static BigDecimal calculateAnnualizedReturn(BigDecimal dailyIRR) {
        if (dailyIRR == null) {
            throw new IllegalArgumentException("日IRR不能为空");
        }
        // 年化收益率 = 日IRR * 365
        BigDecimal annualized = dailyIRR.multiply(new BigDecimal(DAYS_PER_YEAR));
        return annualized.setScale(6, RoundingMode.HALF_UP);
    }

    /**
     * 计算年化收益率
     *
     * @param records 产品收益记录列表（需按日期排序）
     * @return 年化收益率（小数形式，如0.04178表示4.178%）
     */
    public static BigDecimal calculateAnnualizedReturnByRecords(List<ProfitRecord> records) {
        BigDecimal dailyIRR1 = calculateDailyIRR(records);
        BigDecimal annual1 = calculateAnnualizedReturn(dailyIRR1);
        return annual1;
    }

    /**
     * 计算最大回撤（需补充字段）
     * 最大回撤 = (历史峰值 - 后续谷值) / 历史峰值 * 100%
     *
     * @param records 产品所有收益记录（需按日期排序）
     * @return 最大回撤（百分比形式，如-5.2表示最大回撤5.2%）
     */
    public static BigDecimal calculateMaxDrawdown(List<ProfitRecord> records) {
        validateProfitRecordFields(records);
        List<ProfitRecord> sortedRecords = sortRecordsByDate(records);
        if (sortedRecords.size() < 2) {
            log.warn("计算最大回撤缺少数据：产品历史市值序列（需至少2条ProfitRecord记录）");
            return null;
        }

        BigDecimal maxDrawdown = BigDecimal.ZERO;
        BigDecimal peak = sortedRecords.get(0).getTotalAmount(); // 初始峰值

        for (ProfitRecord record : sortedRecords) {
            BigDecimal currentValue = record.getTotalAmount();
            // 更新峰值
            if (currentValue.compareTo(peak) > 0) {
                peak = currentValue;
            }
            // 计算当前回撤
            BigDecimal drawdown = currentValue.subtract(peak).divide(peak, 6, RoundingMode.HALF_UP);
            // 最大回撤取最小值（负数越大表示回撤越大）
            if (drawdown.compareTo(maxDrawdown) < 0) {
                maxDrawdown = drawdown;
            }
        }

        return maxDrawdown.multiply(PERCENT).setScale(4, RoundingMode.HALF_UP);
    }

    /**
     * 计算夏普比率
     * 夏普比率 = (年化收益率 - 无风险收益率) / 收益波动率
     *
     * @param annualizedReturn 年化收益率(%)
     * @param records          收益记录（计算波动率）
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
     *
     * @param product    产品信息
     * @param recordId   要计算的记录ID
     * @param allRecords 该产品的所有记录
     * @return 更新后的单条记录
     */
    public static ProfitRecord calculateSingleRecord(Product product, Long recordId, List<ProfitRecord> allRecords) {
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
        UpdateProductRecord(product, targetRecord);
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

    public static void UpdateProductRecord(Product product, ProfitRecord profitRecord) {
        if (product == null || profitRecord == null) {
            return;
        }
        //申赎要改变持仓成本
        BigDecimal investmentAmount = product.getInvestAmount();
        if (!BigDecimal.ZERO.equals(profitRecord.getTransactionAmount())) {
            investmentAmount = investmentAmount.add(profitRecord.getTransactionAmount());
        }
        BigDecimal profitAmount = profitRecord.getTotalAmount().subtract(investmentAmount);
        BigDecimal profitRate = profitAmount.divide(investmentAmount, 4, RoundingMode.HALF_UP);
        profitRecord.setProfitRate(profitRate);
        product.setInvestAmount(investmentAmount);
    }

    // ------------------- 测试用例验证 -------------------
    public static void main(String[] args) {
        // 用例1：首次申购288.35，721天后市值312.65
        List<ProfitRecord> case1 = new ArrayList<>();
        ProfitRecord r1 = new ProfitRecord();
        r1.setRecordDate(LocalDate.of(2024, 1, 1));
        r1.setTransactionAmount(new BigDecimal("288.35"));
        r1.setTotalAmount(new BigDecimal("288.35"));
        ProfitRecord r2 = new ProfitRecord();
        r2.setRecordDate(LocalDate.of(2024, 1, 1).plusDays(721));
        r2.setTransactionAmount(new BigDecimal("0"));
        r2.setTotalAmount(new BigDecimal("312.65"));
        case1.add(r1);
        case1.add(r2);
        BigDecimal dailyIRR1 = calculateDailyIRR(case1);
        BigDecimal annual1 = calculateAnnualizedReturn(dailyIRR1);
        log.info("用例1：日IRR={}, 年化收益率={}", dailyIRR1, annual1); // 预期日IRR≈0.0001122，年化≈0.04178

        // 用例2：2026-3-1申购20000，3-9赎回11，3-21赎回10.68+市值19997.77
        List<ProfitRecord> case2 = new ArrayList<>();
        ProfitRecord r3 = new ProfitRecord();
        r3.setRecordDate(LocalDate.of(2026, 3, 1));
        r3.setTransactionAmount(new BigDecimal("20000"));
        r3.setTotalAmount(new BigDecimal("20000"));
        ProfitRecord r4 = new ProfitRecord();
        r4.setRecordDate(LocalDate.of(2026, 3, 9));
        r4.setTransactionAmount(new BigDecimal("-11"));
        r4.setTotalAmount(new BigDecimal("19989"));
        ProfitRecord r5 = new ProfitRecord();
        r5.setRecordDate(LocalDate.of(2026, 3, 21));
        r5.setTransactionAmount(new BigDecimal("10.68"));
        r5.setTotalAmount(new BigDecimal("19997.77"));
        case2.add(r3);
        case2.add(r4);
        case2.add(r5);
        BigDecimal dailyIRR2 = calculateDailyIRR(case2);
        BigDecimal annual2 = calculateAnnualizedReturn(dailyIRR2);
        log.info("用例2：日IRR={}, 年化收益率={}", dailyIRR2, annual2); // 预期日IRR≈0.00004872，年化≈0.0179

        // 用例3：2026-03-09申购197660，3-21市值197598.98
        List<ProfitRecord> case3 = new ArrayList<>();
        ProfitRecord r6 = new ProfitRecord();
        r6.setRecordDate(LocalDate.of(2026, 3, 9));
        r6.setTransactionAmount(new BigDecimal("197660"));
        r6.setTotalAmount(new BigDecimal("197660"));
        ProfitRecord r7 = new ProfitRecord();
        r7.setRecordDate(LocalDate.of(2026, 3, 21));
        r7.setTransactionAmount(new BigDecimal("0"));
        r7.setTotalAmount(new BigDecimal("197598.98"));
        case3.add(r6);
        case3.add(r7);
        BigDecimal dailyIRR3 = calculateDailyIRR(case3);
        BigDecimal annual3 = calculateAnnualizedReturn(dailyIRR3);
        log.info("用例3：日IRR={}, 年化收益率={}", dailyIRR3, annual3); // 预期日IRR≈-0.0000257，年化≈-0.00935
    }
}