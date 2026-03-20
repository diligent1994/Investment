package com.dq.investment.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("t_product")
public class Product {
    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;                // 产品名称
    private String type;                 // 产品类型：基金、股票、理财、存款等
    private BigDecimal investAmount;           // 购买金额
    private LocalDate buyDate;       // 购买日期
    private BigDecimal expectedYield; // 预期年化收益率（%）
    private String description;           // 备注
    private String status;
    private String riskLevel;

    // 新增指标字段
    private BigDecimal annualizedReturn; // 累计年化收益率
    private BigDecimal maxDrawdown;      // 最大回撤(%)
    private String liquidity;            // 流动性（高/中/低）
    private BigDecimal sharpeRatio;      // 夏普比率
    private BigDecimal feeRate;          // 综合费率(%)


    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;              // 逻辑删除：0未删除，1已删除
}