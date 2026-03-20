package com.dq.investment.controller;

import com.dq.investment.common.Result;
import com.dq.investment.common.PageResult;
import com.dq.investment.entity.Product;
import com.dq.investment.service.ProductService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/product")
public class ProductController {
    @Autowired
    private ProductService productService;

    @GetMapping("/page")
    public Result<PageResult<Product>> pageList(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String type) {
        return Result.success(productService.pageList(pageNum, pageSize, name, type));
    }

    @PostMapping
    public Result<Boolean> add(@RequestBody Product product) {
        return productService.save(product) ? Result.success("新增成功", true) : Result.error("新增失败");
    }

    @PutMapping
    public Result<Boolean> update(@RequestBody Product product) {
        return productService.updateById(product) ? Result.success("更新成功", true) : Result.error("更新失败");
    }

    @DeleteMapping("/{id}")
    public Result<Boolean> delete(@PathVariable Long id) {
        return productService.removeById(id) ? Result.success("删除成功", true) : Result.error("删除失败");
    }

    @GetMapping("/{id}")
    public Result<Product> getById(@PathVariable Long id) {
        return Result.success(productService.getById(id));
    }
}