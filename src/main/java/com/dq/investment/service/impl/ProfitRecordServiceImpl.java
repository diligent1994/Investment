package com.dq.investment.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dq.investment.common.PageResult;
import com.dq.investment.entity.ProfitRecord;
import com.dq.investment.entity.Product;
import com.dq.investment.mapper.ProfitRecordMapper;
import com.dq.investment.mapper.ProductMapper;
import com.dq.investment.service.ProfitRecordService;
import com.dq.investment.util.CalculateUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Service
public class ProfitRecordServiceImpl extends ServiceImpl<ProfitRecordMapper, ProfitRecord> implements ProfitRecordService {

    @Autowired
    private ProductMapper productMapper;

    @Override
    public List<ProfitRecord> listByProductId(Long productId) {
        LambdaQueryWrapper<ProfitRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ProfitRecord::getProductId, productId)
                .orderByAsc(ProfitRecord::getRecordDate); // 按日期排序
        return list(wrapper);
    }

    @Override
    public PageResult<ProfitRecord> pageList(Integer pageNum, Integer pageSize, Long productId) {
        Page<ProfitRecord> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<ProfitRecord> wrapper = new LambdaQueryWrapper<>();
        if (productId != null) {
            wrapper.eq(ProfitRecord::getProductId, productId);
        }
        wrapper.orderByDesc(ProfitRecord::getRecordDate);
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
        BigDecimal annualized = CalculateUtil.calculateAnnualizedReturnByRecords(allRecords);
        BigDecimal maxDrawdown = CalculateUtil.calculateMaxDrawdown(allRecords);
        BigDecimal sharpe = CalculateUtil.calculateSharpeRatio(annualized, allRecords);

        // 4. 更新当前记录的指标
        profitRecord.setAnnualizedReturn(annualized.multiply(CalculateUtil.PERCENT));
        profitRecord.setMaxDrawdown(maxDrawdown.multiply(CalculateUtil.PERCENT));
        profitRecord.setSharpeRatio(sharpe);
        this.updateById(profitRecord);

        // 5. 更新产品表的累计指标
        if (product != null) {
            product.setAnnualizedReturn(annualized);
            product.setMaxDrawdown(maxDrawdown);
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