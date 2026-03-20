CREATE DATABASE IF NOT EXISTS investment DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE investment;

CREATE TABLE IF NOT EXISTS t_product (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL COMMENT '产品名称',
    type VARCHAR(50) NOT NULL COMMENT '产品类型',
    invest_amount DECIMAL(10,2) NOT NULL COMMENT '购买金额',
    buy_date DATE NOT NULL COMMENT '购买日期',
    expected_yield DECIMAL(5,2) COMMENT '预期年化收益率(%)',
    description VARCHAR(500) COMMENT '备注',
    `risk_level` varchar(10) DEFAULT NULL COMMENT '风险等级',
    `status` varchar(20) DEFAULT '持有' COMMENT '状态',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT DEFAULT 0 COMMENT '逻辑删除'
) COMMENT '理财产品表';

-- 收益记录表
CREATE TABLE `t_profit_record` (
       `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
       `product_id` bigint NOT NULL COMMENT '关联理财产品ID',
       `profit_amount` decimal(12,2) DEFAULT NULL COMMENT '本次收益金额',
       `profit_rate` decimal(10,2) DEFAULT NULL COMMENT '本次收益率(%)',
       `record_date` date DEFAULT NULL COMMENT '记录日期',
       `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
       `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
       `deleted` tinyint DEFAULT '0' COMMENT '逻辑删除',
       PRIMARY KEY (`id`),
       KEY `idx_product_id` (`product_id`)
) COMMENT='收益记录表';