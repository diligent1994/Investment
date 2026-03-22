package com.dq.investment.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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
import java.time.LocalDateTime;
import java.util.Date;
import java.util.List;

@Service
public class ProductServiceImpl extends ServiceImpl<ProductMapper, Product> implements ProductService {

    @Autowired
    private ProfitRecordService profitRecordService;

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
    public PageResult<Product> pageList(Integer pageNum, Integer pageSize, String name, String type) {
        Page<Product> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<Product> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(name)) {
            wrapper.like(Product::getName, name);
        }
        if (StringUtils.hasText(type)) {
            wrapper.eq(Product::getType, type);
        }
        wrapper.orderByDesc(Product::getCreateTime);
        this.page(page, wrapper);
        return new PageResult<>(page.getTotal(), page.getPages(), page.getCurrent(), page.getSize(), page.getRecords());
    }
}