package com.dq.investment.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.dq.investment.common.PageResult;
import com.dq.investment.entity.Product;

public interface ProductService extends IService<Product> {
    PageResult<Product> pageList(Integer pageNum, Integer pageSize, String name, String type);
}