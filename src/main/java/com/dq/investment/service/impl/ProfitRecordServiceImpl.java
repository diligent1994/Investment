package com.dq.investment.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dq.investment.common.PageResult;
import com.dq.investment.entity.ProfitRecord;
import com.dq.investment.mapper.ProfitRecordMapper;
import com.dq.investment.service.ProfitRecordService;
import org.springframework.stereotype.Service;

@Service
public class ProfitRecordServiceImpl extends ServiceImpl<ProfitRecordMapper, ProfitRecord> implements ProfitRecordService {
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
}