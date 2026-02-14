package com.xhx.web.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xhx.common.result.Result;
import com.xhx.core.service.management.DataSourceService;
import com.xhx.dal.entity.DataSource;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 数据源管理
 * @author master
 */
@RestController
@RequestMapping("/api/data-sources")
@RequiredArgsConstructor
public class DataSourceController {

    private final DataSourceService dataSourceService;

    /**
     * 分页查询数据源列表
     */
    @GetMapping
    @PreAuthorize("hasAuthority('ADMIN')")
    public Result<Page<DataSource>> page(
            @RequestParam(defaultValue = "1") int current,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String connName) {
        return Result.success(dataSourceService.getDataSourcePage(current, size, connName));
    }

    /**
     * 测试连接 (不入库)
     */
    @PostMapping("/test")
    @PreAuthorize("hasAuthority('ADMIN')")
    public Result<Void> test(@RequestBody DataSource ds) {
        dataSourceService.testConnection(ds);
        return Result.success();
    }

    /**
     * 新增数据源
     */
    @PostMapping
    @PreAuthorize("hasAuthority('ADMIN')")
    public Result<Void> save(@RequestBody DataSource ds) {
        dataSourceService.addDataSource(ds);
        return Result.success();
    }

    /**
     * 修改数据源
     */
    @PutMapping
    @PreAuthorize("hasAuthority('ADMIN')")
    public Result<Void> update(@RequestBody DataSource ds) {
        dataSourceService.updateDataSource(ds);
        return Result.success();
    }

    /**
     * 删除数据源
     * 逻辑删除：标记 is_deleted = 1 并释放连接池
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('ADMIN')")
    public Result<Void> delete(@PathVariable Long id) {
        dataSourceService.deleteDataSource(id);
        return Result.success();
    }

    /**
     * 获取数据源下的所有表名
     */
    @GetMapping("/{id}/tables")
    @PreAuthorize("hasAuthority('ADMIN')")
    public Result<List<String>> getTables(@PathVariable Long id) {
        return Result.success(dataSourceService.getTableNames(id));
    }
}