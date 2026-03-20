package com.dq.investment.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.dq.investment.common.PageResult;
import com.dq.investment.entity.ProfitRecord;

public interface ProfitRecordService extends IService<ProfitRecord> {
    PageResult<ProfitRecord> pageList(Integer pageNum, Integer pageSize, Long productId);
}