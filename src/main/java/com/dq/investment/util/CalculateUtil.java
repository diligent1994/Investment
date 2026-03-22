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
 * 优化说明：
 * 1. 年化收益率改为复利公式 (1+日IRR)^365 - 1
 * 2. 改进 IRR 求解的初始猜测（尝试正负值）
 * 3. 夏普比率基于市值序列计算期间收益率，并年化波动率
 * 4. 添加字段完整性校验
 */
@Slf4j
public class CalculateUtil {
    // 迭代精度（IRR计算收敛阈值）
    private static final BigDecimal IRR_PRECISION = new BigDecimal("0.00000001");
    // 最大迭代次数
    private static final int MAX_ITERATION = 1000;
    // 无风险利率（年化，默认国债利率2%）
    private static final BigDecimal RISK_FREE_RATE = new BigDecimal("0.02");
    // 百分比转换系数
    public static final BigDecimal PERCENT = new BigDecimal("100");
    // 年化天数（自然年）
    private static final int DAYS_PER_YEAR = 365;
    // 复利计算精度
    private static final int COMPOUND_PRECISION = 10;

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
        sortedRecords.sort(Comparator.comparing(ProfitRecord::getRecordDate));
        return sortedRecords;
    }

    /**
     * 计算日IRR（内部收益率）
     *
     * @param sortedRecords 产品收益记录列表（需按日期排序）
     * @return 日IRR（小数形式，如0.0001122表示日IRR为0.01122%）
     */
    public static BigDecimal calculateDailyIRR(List<ProfitRecord> sortedRecords) {
        validateProfitRecordFields(sortedRecords);
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
        // 最后一条记录加上期末市值（视为最终赎回）
        ProfitRecord lastRecord = sortedRecords.get(sortedRecords.size() - 1);
        cashFlows.set(cashFlows.size() - 1, cashFlows.get(cashFlows.size() - 1).add(lastRecord.getTotalAmount()));

        // 尝试正负初始猜测，提高收敛性
        BigDecimal irr = null;
        for (double guess : new double[]{0.0001, -0.0001}) {
            irr = newtonRaphson(guess, cashFlows, daysList);
            if (irr != null) {
                break;
            }
        }
        if (irr == null) {
            log.warn("IRR 求解未收敛，返回0");
            return BigDecimal.ZERO;
        }
        return irr.setScale(8, RoundingMode.HALF_UP); // 返回百分比形式的日 IRR
    }

    private static BigDecimal newtonRaphson(double initialGuess, List<BigDecimal> cashFlows, List<Integer> daysList) {
        BigDecimal irr = BigDecimal.valueOf(initialGuess);
        for (int i = 0; i < MAX_ITERATION; i++) {
            BigDecimal npv = calculateNPV(irr, cashFlows, daysList);
            BigDecimal npvDerivative = calculateNPVDerivative(irr, cashFlows, daysList);

            if (npvDerivative.abs().compareTo(IRR_PRECISION) < 0) {
                return null; // 导数接近0，停止迭代
            }

            BigDecimal newIrr = irr.subtract(npv.divide(npvDerivative, 10, RoundingMode.HALF_UP));
            if (newIrr.subtract(irr).abs().compareTo(IRR_PRECISION) < 0) {
                return newIrr; // 收敛，停止迭代
            }
            irr = newIrr;
            // 防止发散
            if (irr.abs().compareTo(new BigDecimal("10")) > 0) {
                return null;
            }
        }
        return null;
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
     * 计算年化收益率（复利公式：(1 + 日IRR)^365 - 1）
     *
     * @param dailyIRR 日IRR（小数形式，如0.0001122）
     * @return 年化收益率（小数形式，如0.04178表示4.178%）
     */
    public static BigDecimal calculateAnnualizedReturn(BigDecimal dailyIRR) {
        if (dailyIRR == null) {
            throw new IllegalArgumentException("日IRR不能为空");
        }
        // 复利公式：(1 + 日IRR)^365 - 1
        BigDecimal annualized = BigDecimal.ONE.add(dailyIRR).pow(DAYS_PER_YEAR).subtract(BigDecimal.ONE);
        return annualized.setScale(6, RoundingMode.HALF_UP);
    }

    /**
     * 计算年化收益率（基于记录）
     *
     * @param records 产品收益记录列表（需按日期排序）
     * @return 年化收益率（小数形式，如0.04178表示4.178%）
     */
    public static BigDecimal calculateAnnualizedReturnByRecords(List<ProfitRecord> records) {
        BigDecimal dailyIRR = calculateDailyIRR(records);
        return calculateAnnualizedReturn(dailyIRR);
    }

    /**
     * 计算最大回撤（基于净值序列）
     * 最大回撤 = (历史峰值 - 后续谷值) / 历史峰值 * 100%
     *
     * @param sortedRecords 产品所有收益记录（需按日期排序）
     * @return 最大回撤（百分比形式，如-5.2表示最大回撤5.2%）
     */
    public static BigDecimal calculateMaxDrawdown(List<ProfitRecord> sortedRecords) {
        validateProfitRecordFields(sortedRecords);
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
            // 计算当前回撤 = (当前值 - 峰值) / 峰值
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
     * @param annualizedReturn 年化收益率
     * @param records          收益记录（计算波动率）
     * @return 夏普比率
     */
    public static BigDecimal calculateSharpeRatio(BigDecimal annualizedReturn, List<ProfitRecord> records) {
        if (records.size() < 2 || annualizedReturn.compareTo(RISK_FREE_RATE) <= 0) {
            return BigDecimal.ZERO;
        }

        // 计算历史每日收益率
        List<BigDecimal> dailyReturns = calculateDailyReturns(records);
        if (dailyReturns.isEmpty()) {
            return BigDecimal.ZERO;
        }

        // 计算收益波动率（标准差）
        BigDecimal stdDev = calculateStandardDeviation(dailyReturns);

        if (stdDev.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }

        // 年化波动率 = 日波动率 * sqrt(252)（假设252个交易日）
        BigDecimal annualVolatility = stdDev.multiply(BigDecimal.valueOf(Math.sqrt(252))).setScale(6, RoundingMode.HALF_UP);

        // 夏普比率 = (年化收益率 - 无风险利率) / 年化波动率
        BigDecimal sharpe = annualizedReturn.subtract(RISK_FREE_RATE).divide(annualVolatility, 4, RoundingMode.HALF_UP);
        return sharpe.setScale(4, RoundingMode.HALF_UP);
    }

    /**
     * 计算历史每日收益率序列
     */
    private static List<BigDecimal> calculateDailyReturns(List<ProfitRecord> records) {
        List<BigDecimal> dailyReturns = new ArrayList<>();
        // 计算期间收益率（简单收益率）及其天数
        List<BigDecimal> periodReturns = new ArrayList<>();
        List<Integer> periodDays = new ArrayList<>();
        ProfitRecord previous = null;

        for (ProfitRecord record : records) {
            if (previous == null) {
                previous = record;
                continue;
            }

            // 计算日收益率 = (当前总市值 - 上一日总市值) / 上一日总市值
            BigDecimal returnRate = record.getTotalAmount()
                    .subtract(previous.getTotalAmount())
                    .divide(previous.getTotalAmount(), 6, RoundingMode.HALF_UP);

            periodReturns.add(returnRate);
            long days = ChronoUnit.DAYS.between(previous.getRecordDate(), record.getRecordDate());
            periodDays.add((int) days);
            previous = record;
        }

        // 计算年化波动率：先将期间收益率折算为日收益率（假设线性），再计算日标准差，最后年化
        for (int i = 0; i < periodReturns.size(); i++) {
            BigDecimal periodRet = periodReturns.get(i);
            int days = periodDays.get(i);
            if (days > 0) {
                // 简单近似：日收益率 = 期间收益率 / 天数
                BigDecimal dailyRet = periodRet.divide(new BigDecimal(days), 10, RoundingMode.HALF_UP);
                dailyReturns.add(dailyRet);
            }
        }
        return dailyReturns;
    }

    /**
     * 计算标准差（样本标准差）
     */
    private static BigDecimal calculateStandardDeviation(List<BigDecimal> values) {
        if (values.size() < 2) {
            return BigDecimal.ZERO;
        }

        // 计算日收益率均值
        BigDecimal sum = values.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal mean = sum.divide(new BigDecimal(values.size()), 10, RoundingMode.HALF_UP);

        // 计算方差
        BigDecimal variance = values.stream()
                .map(v -> v.subtract(mean).pow(2))
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(new BigDecimal(values.size() - 1), 10, RoundingMode.HALF_UP);

        // 标准差 = sqrt(方差)
        return BigDecimal.valueOf(Math.sqrt(variance.doubleValue()));
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
        List<ProfitRecord> sortedRecords = CalculateUtil.sortRecordsByDate(recordsToTarget);
        BigDecimal annualized = calculateAnnualizedReturnByRecords(sortedRecords);
        BigDecimal maxDrawdown = calculateMaxDrawdown(sortedRecords);
        BigDecimal sharpe = calculateSharpeRatio(annualized, sortedRecords);

        // 更新当前记录
        targetRecord.setAnnualizedReturn(annualized.multiply(PERCENT));
        targetRecord.setMaxDrawdown(maxDrawdown.multiply(PERCENT));
        targetRecord.setSharpeRatio(sharpe);
        return targetRecord;
    }

    /**
     * 更新产品持仓成本（用于计算收益）
     */
    public static void UpdateProductRecord(Product product, ProfitRecord profitRecord) {
        if (product == null || profitRecord == null) {
            return;
        }
        // 申赎要改变持仓成本
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
        r2.setTransactionAmount(BigDecimal.ZERO);
        r2.setTotalAmount(new BigDecimal("312.65"));
        case1.add(r1);
        case1.add(r2);
        BigDecimal annual1 = calculateAnnualizedReturnByRecords(case1);
        log.info("用例1：年化收益率={}（预期：0.04178）", annual1);
        // 预期：0.04178（复利计算）

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
        BigDecimal annual2 = calculateAnnualizedReturnByRecords(case2);
        log.info("用例2：年化收益率={}（预期：0.0179）", annual2);
        // 预期：0.0179（复利计算）

        // 用例3：2026-03-09申购197660，3-21市值197598.98
        List<ProfitRecord> case3 = new ArrayList<>();
        ProfitRecord r6 = new ProfitRecord();
        r6.setRecordDate(LocalDate.of(2026, 3, 9));
        r6.setTransactionAmount(new BigDecimal("197660"));
        r6.setTotalAmount(new BigDecimal("197660"));
        ProfitRecord r7 = new ProfitRecord();
        r7.setRecordDate(LocalDate.of(2026, 3, 21));
        r7.setTransactionAmount(BigDecimal.ZERO);
        r7.setTotalAmount(new BigDecimal("197598.98"));
        case3.add(r6);
        case3.add(r7);
        BigDecimal annual3 = calculateAnnualizedReturnByRecords(case3);
        log.info("用例3：年化收益率={}（预期：-0.00935）", annual3);
        // 预期：-0.00935（复利计算）
    }
}