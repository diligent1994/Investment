package com.dq.investment.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.dq.investment.common.PageResult;
import com.dq.investment.entity.ProfitRecord;

public interface ProfitRecordService extends IService<ProfitRecord> {
    PageResult<ProfitRecord> pageList(Integer pageNum, Integer pageSize, Long productId);

    // 新增：保存记录并自动计算指标
    boolean saveWithCalculate(ProfitRecord profitRecord);

    // 新增：手动计算单条记录指标
    boolean calculateSingleRecord(Long productId, Long recordId);
}