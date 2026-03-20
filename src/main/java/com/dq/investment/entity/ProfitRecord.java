package com.dq.investment.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("t_profit_record")
public class ProfitRecord {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long productId;               // 关联产品ID
    private LocalDate recordDate;          // 收益日期
    private BigDecimal profitAmount;       // 收益金额
    private BigDecimal profitRate;          // 收益率（%），可选

    // 新增字段：支持申赎+动态指标
    private String transactionType;      // 交易类型：申购/赎回/收益更新
    private BigDecimal transactionAmount;// 申赎金额（申购+，赎回-）
    private BigDecimal annualizedReturn; // 截至本次的年化收益率(%)
    private BigDecimal maxDrawdown;      // 截至本次的最大回撤(%)
    private BigDecimal sharpeRatio;      // 截至本次的夏普比率

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;
}