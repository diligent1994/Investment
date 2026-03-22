package com.dq.investment.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dq.investment.common.PageResult;
import com.dq.investment.entity.Product;
import com.dq.investment.entity.ProfitRecord;
import com.dq.investment.mapper.ProductMapper;
import com.dq.investment.service.ProductService;
import com.dq.investment.service.ProfitRecordService;
import com.dq.investment.util.CalculateUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author hasee
 */
@Service
public class ProductServiceImpl extends ServiceImpl<ProductMapper, Product> implements ProductService {

    @Autowired
    private ProfitRecordService profitRecordService;

    // 排序字段白名单 + Lambda 映射
    public static final Map<String, SFunction<Product, ?>> SORT_FIELD_LAMBDA_MAP;

    static {
        SORT_FIELD_LAMBDA_MAP = new HashMap<>(16);
        // 基础字段
        SORT_FIELD_LAMBDA_MAP.put("id", Product::getId);
        SORT_FIELD_LAMBDA_MAP.put("name", Product::getName);
        SORT_FIELD_LAMBDA_MAP.put("type", Product::getType);
        SORT_FIELD_LAMBDA_MAP.put("investAmount", Product::getInvestAmount);
        SORT_FIELD_LAMBDA_MAP.put("buyDate", Product::getBuyDate);
        SORT_FIELD_LAMBDA_MAP.put("expectedYield", Product::getExpectedYield);
        SORT_FIELD_LAMBDA_MAP.put("description", Product::getDescription);
        SORT_FIELD_LAMBDA_MAP.put("status", Product::getStatus);
        SORT_FIELD_LAMBDA_MAP.put("riskLevel", Product::getRiskLevel);

        // 新增指标字段
        SORT_FIELD_LAMBDA_MAP.put("annualizedReturn", Product::getAnnualizedReturn);
        SORT_FIELD_LAMBDA_MAP.put("maxDrawdown", Product::getMaxDrawdown);
        SORT_FIELD_LAMBDA_MAP.put("liquidity", Product::getLiquidity);
        SORT_FIELD_LAMBDA_MAP.put("sharpeRatio", Product::getSharpeRatio);
        SORT_FIELD_LAMBDA_MAP.put("feeRate", Product::getFeeRate);

        // 公共字段（创建/更新时间、逻辑删除）
        SORT_FIELD_LAMBDA_MAP.put("createTime", Product::getCreateTime);
        SORT_FIELD_LAMBDA_MAP.put("updateTime", Product::getUpdateTime);
        SORT_FIELD_LAMBDA_MAP.put("deleted", Product::getDeleted);
    }

    // 新增：重写save方法，新增产品后插入申购记录
    @Override
    @Transactional(rollbackFor = Exception.class) // 事务保证：产品新增失败则申购记录也回滚
    public boolean save(Product product) {
        // 1. 保存产品
        boolean saveFlag = super.save(product);
        if (!saveFlag) {
            return false;
        }

        // 2. 自动插入申购记录到收益表
        ProfitRecord buyRecord = new ProfitRecord();
        buyRecord.setProductId(product.getId()); // 关联新产品ID
        buyRecord.setTransactionType("申购"); // 交易类型：申购
        buyRecord.setTransactionAmount(product.getInvestAmount()); // 申购金额=产品投资金额
        buyRecord.setTotalAmount(product.getInvestAmount()); // 持仓总金额=产品投资金额
        buyRecord.setProfitAmount(BigDecimal.ZERO); // 申购时无收益
        buyRecord.setProfitRate(BigDecimal.ZERO); // 申购时收益率0
        buyRecord.setRecordDate(product.getBuyDate()); // 申购日期=产品购买日期
        buyRecord.setAnnualizedReturn(BigDecimal.ZERO); // 初始指标0
        buyRecord.setMaxDrawdown(BigDecimal.ZERO);
        buyRecord.setSharpeRatio(BigDecimal.ZERO);

        // 3. 保存申购记录
        profitRecordService.save(buyRecord);
        return true;
    }

    // 新增：计算并更新产品最新指标
    @Override
    public void calculateAndUpdateIndicators(Long productId) {
        // 1. 获取产品信息
        Product product = getById(productId);
        if (product == null) {
            return;
        }

        // 2. 获取该产品所有申赎/收益记录
        List<ProfitRecord> records = profitRecordService.listByProductId(productId);
        if (records.isEmpty()) {
            // 无记录时重置指标
            product.setAnnualizedReturn(BigDecimal.ZERO);
            product.setMaxDrawdown(BigDecimal.ZERO);
            product.setSharpeRatio(BigDecimal.ZERO);
            updateById(product);
            return;
        }

        // 3. 调用工具类计算指标（复用之前的精准年化收益率工具）
        List<ProfitRecord> sortedRecords = CalculateUtil.sortRecordsByDate(records);
        BigDecimal annualRate = CalculateUtil.calculateAnnualizedReturnByRecords(sortedRecords);
        BigDecimal maxDrawdown = CalculateUtil.calculateMaxDrawdown(sortedRecords);
        BigDecimal sharpeRatio = CalculateUtil.calculateSharpeRatio(annualRate, sortedRecords);

        // 4. 更新产品指标
        product.setAnnualizedReturn(annualRate.multiply(CalculateUtil.PERCENT));
        product.setMaxDrawdown(maxDrawdown.multiply(CalculateUtil.PERCENT));
        product.setSharpeRatio(sharpeRatio);
        product.setInvestAmount(records.stream()
                // 提取每个元素的 transactionAmount，空值默认0（避免空指针）
                .map(record -> record.getTransactionAmount() == null ? BigDecimal.ZERO : record.getTransactionAmount())
                // 累加：初始值ZERO，累加器BigDecimal.add
                .reduce(BigDecimal.ZERO, BigDecimal::add));
        updateById(product);
    }

    @Override
    public PageResult<Product> pageList(Integer pageNum, Integer pageSize, String name, String type,
                                        String sortField, String sortDir) {
        Page<Product> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<Product> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(name)) {
            wrapper.like(Product::getName, name);
        }
        if (StringUtils.hasText(type)) {
            wrapper.eq(Product::getType, type);
        }

        // 排序逻辑（Lambda方式）
        if (StringUtils.hasText(sortField) && SORT_FIELD_LAMBDA_MAP.containsKey(sortField)) {
            boolean isAsc = "asc".equalsIgnoreCase(sortDir);
            SFunction<Product, ?> lambdaField = SORT_FIELD_LAMBDA_MAP.get(sortField);
            if (isAsc) {
                wrapper.orderByAsc(lambdaField);
            } else {
                wrapper.orderByDesc(lambdaField);
            }
        }

        this.page(page, wrapper);
        return new PageResult<>(page.getTotal(), page.getPages(), page.getCurrent(), page.getSize(), page.getRecords());
    }
}