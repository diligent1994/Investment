package com.dq.investment.controller;

import com.dq.investment.common.Result;
import com.dq.investment.common.PageResult;
import com.dq.investment.entity.ProfitRecord;
import com.dq.investment.service.ProfitRecordService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/profit")
public class ProfitRecordController {
    @Autowired
    private ProfitRecordService profitRecordService;

    @GetMapping("/page")
    public Result<PageResult<ProfitRecord>> pageList(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) Long productId) {
        return Result.success(profitRecordService.pageList(pageNum, pageSize, productId));
    }

    @PostMapping
    public Result<Boolean> add(@RequestBody ProfitRecord profitRecord) {
        return profitRecordService.save(profitRecord) ? Result.success("新增成功", true) : Result.error("新增失败");
    }

    @PutMapping
    public Result<Boolean> update(@RequestBody ProfitRecord profitRecord) {
        return profitRecordService.updateById(profitRecord) ? Result.success("更新成功", true) : Result.error("更新失败");
    }

    @DeleteMapping("/{id}")
    public Result<Boolean> delete(@PathVariable Long id) {
        return profitRecordService.removeById(id) ? Result.success("删除成功", true) : Result.error("删除失败");
    }

    @GetMapping("/{id}")
    public Result<ProfitRecord> getById(@PathVariable Long id) {
        return Result.success(profitRecordService.getById(id));
    }
}