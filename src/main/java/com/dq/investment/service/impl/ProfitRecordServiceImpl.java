package com.dq.investment.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dq.investment.common.PageResult;
import com.dq.investment.entity.Product;
import com.dq.investment.entity.ProfitRecord;
import com.dq.investment.mapper.ProductMapper;
import com.dq.investment.mapper.ProfitRecordMapper;
import com.dq.investment.service.ProfitRecordService;
import com.dq.investment.util.CalculateUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author hasee
 */
@Service
public class ProfitRecordServiceImpl extends ServiceImpl<ProfitRecordMapper, ProfitRecord> implements ProfitRecordService {

    @Autowired
    private ProductMapper productMapper;

    // 排序字段白名单 + 字段名 -> Lambda 映射（方案1：Lambda方式）
    public static final Map<String, SFunction<ProfitRecord, ?>> SORT_FIELD_LAMBDA_MAP;

    static {
        SORT_FIELD_LAMBDA_MAP = new HashMap<>(16);
        // 基础录入字段
        SORT_FIELD_LAMBDA_MAP.put("id", ProfitRecord::getId);
        SORT_FIELD_LAMBDA_MAP.put("productId", ProfitRecord::getProductId);
        SORT_FIELD_LAMBDA_MAP.put("recordDate", ProfitRecord::getRecordDate);
        SORT_FIELD_LAMBDA_MAP.put("transactionType", ProfitRecord::getTransactionType);
        SORT_FIELD_LAMBDA_MAP.put("transactionAmount", ProfitRecord::getTransactionAmount);
        SORT_FIELD_LAMBDA_MAP.put("totalAmount", ProfitRecord::getTotalAmount);

        // 自动计算字段
        SORT_FIELD_LAMBDA_MAP.put("profitAmount", ProfitRecord::getProfitAmount);
        SORT_FIELD_LAMBDA_MAP.put("profitRate", ProfitRecord::getProfitRate);
        SORT_FIELD_LAMBDA_MAP.put("annualizedReturn", ProfitRecord::getAnnualizedReturn);
        SORT_FIELD_LAMBDA_MAP.put("maxDrawdown", ProfitRecord::getMaxDrawdown);
        SORT_FIELD_LAMBDA_MAP.put("sharpeRatio", ProfitRecord::getSharpeRatio);

        // 公共字段（创建/更新时间、逻辑删除）
        SORT_FIELD_LAMBDA_MAP.put("createTime", ProfitRecord::getCreateTime);
        SORT_FIELD_LAMBDA_MAP.put("updateTime", ProfitRecord::getUpdateTime);
        SORT_FIELD_LAMBDA_MAP.put("deleted", ProfitRecord::getDeleted);
    }


    @Override
    public List<ProfitRecord> listByProductId(Long productId) {
        LambdaQueryWrapper<ProfitRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ProfitRecord::getProductId, productId)
                .orderByAsc(ProfitRecord::getRecordDate); // 按日期排序
        return list(wrapper);
    }

    @Override
    public PageResult<ProfitRecord> pageList(Integer pageNum, Integer pageSize, Long productId,
                                             String sortField, String sortDir) {
        // 1. 初始化分页对象
        Page<ProfitRecord> page = new Page<>(pageNum, pageSize);

        // 2. 构建查询条件
        LambdaQueryWrapper<ProfitRecord> wrapper = new LambdaQueryWrapper<>();
        if (productId != null) {
            wrapper.eq(ProfitRecord::getProductId, productId);
        }

        // 排序逻辑（Lambda方式）
        if (StringUtils.hasText(sortField) && SORT_FIELD_LAMBDA_MAP.containsKey(sortField)) {
            boolean isAsc = "asc".equalsIgnoreCase(sortDir);
            SFunction<ProfitRecord, ?> lambdaField = SORT_FIELD_LAMBDA_MAP.get(sortField);
            wrapper.orderBy(true, isAsc, lambdaField);
        } else {
            wrapper.orderByDesc(ProfitRecord::getProductId);
        }

        // 4. 执行分页查询（MP自动拼接 ORDER BY 语句到SQL）
        this.page(page, wrapper);
        return new PageResult<>(page.getTotal(), page.getPages(), page.getCurrent(), page.getSize(), page.getRecords());
    }

    /**
     * 保存记录并自动计算指标（更新当前记录+产品累计指标）
     */
    @Override
    public boolean saveWithCalculate(ProfitRecord profitRecord) {
        Long productId = profitRecord.getProductId();
        Product product = productMapper.selectById(productId);
        //申赎要改变持仓成本
        CalculateUtil.UpdateProductRecord(product, profitRecord);

        // 1. 保存当前记录
        boolean saveFlag = this.saveOrUpdate(profitRecord);
        if (!saveFlag) {
            return false;
        }

        // 2. 获取该产品的所有记录

        LambdaQueryWrapper<ProfitRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ProfitRecord::getProductId, productId);
        List<ProfitRecord> allRecords = this.list(wrapper);

        // 3. 计算产品级指标
        List<ProfitRecord> sortedRecords = CalculateUtil.sortRecordsByDate(allRecords);
        BigDecimal annualized = CalculateUtil.calculateAnnualizedReturnByRecords(sortedRecords);
        BigDecimal maxDrawdown = CalculateUtil.calculateMaxDrawdown(sortedRecords);
        BigDecimal sharpe = CalculateUtil.calculateSharpeRatio(annualized, sortedRecords);

        // 4. 更新当前记录的指标
        profitRecord.setAnnualizedReturn(annualized.multiply(CalculateUtil.PERCENT));
        profitRecord.setMaxDrawdown(maxDrawdown.multiply(CalculateUtil.PERCENT));
        profitRecord.setSharpeRatio(sharpe);
        this.updateById(profitRecord);

        // 5. 更新产品表的累计指标
        if (product != null) {
            product.setAnnualizedReturn(annualized.multiply(CalculateUtil.PERCENT));
            product.setMaxDrawdown(maxDrawdown.multiply(CalculateUtil.PERCENT));
            product.setSharpeRatio(sharpe);
            productMapper.updateById(product);
        }

        return true;
    }

    /**
     * 手动计算单条记录指标
     */
    @Override
    public boolean calculateSingleRecord(Long productId, Long recordId) {
        // 获取该产品所有记录
        LambdaQueryWrapper<ProfitRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ProfitRecord::getProductId, productId);
        List<ProfitRecord> allRecords = this.list(wrapper);
        Product product = productMapper.selectById(productId);
        // 计算单条记录指标
        ProfitRecord updatedRecord = CalculateUtil.calculateSingleRecord(product, recordId, allRecords);
        if (updatedRecord == null) {
            return false;
        }

        // 更新数据库
        return this.updateById(updatedRecord);
    }
}