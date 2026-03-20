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

    /**
     * 新增/更新收益记录（自动计算指标）
     */
    @PostMapping
    public Result<Boolean> add(@RequestBody ProfitRecord profitRecord) {
        return profitRecordService.saveWithCalculate(profitRecord)
                ? Result.success("新增成功并计算指标", true)
                : Result.error("新增失败");
    }

    @PutMapping
    public Result<Boolean> update(@RequestBody ProfitRecord profitRecord) {
        return profitRecordService.saveWithCalculate(profitRecord)
                ? Result.success("更新成功并计算指标", true)
                : Result.error("更新失败");
    }

    /**
     * 手动计算单条记录指标
     */
    @PostMapping("/calculate/{productId}/{recordId}")
    public Result<Boolean> calculateSingleRecord(
            @PathVariable Long productId,
            @PathVariable Long recordId) {
        return profitRecordService.calculateSingleRecord(productId, recordId)
                ? Result.success("手动计算指标成功", true)
                : Result.error("计算失败");
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