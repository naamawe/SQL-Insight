package com.xhx.web.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xhx.common.context.UserContext;
import com.xhx.common.exception.ServiceException;
import com.xhx.common.result.Result;
import com.xhx.core.model.dto.DataSourceSaveDTO;
import com.xhx.core.model.dto.DataSourceUpdateDTO;
import com.xhx.core.model.vo.DataSourceVO;
import com.xhx.core.service.management.DataSourceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static com.xhx.common.constant.SystemPermissionConstants.*;

/**
 * 数据源管理
 * @author master
 */
@RestController
@RequestMapping("/api/data-sources")
@RequiredArgsConstructor
@PreAuthorize("hasRole('" + ADMIN + "')")
public class DataSourceController {

    private final DataSourceService dataSourceService;

    /**
     * 分页查询所有数据源（管理员）
     */
    @GetMapping("/admin/page")
    public Result<Page<DataSourceVO>> page(
            @RequestParam(defaultValue = "1") int current,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String connName) {
        // 限制单次最多查询100条
        if (size > 100) {
            size = 100;
        }
        return Result.success(dataSourceService.getDataSourcePage(current, size, connName));
    }

    /**
     * 获取所有数据源列表（不分页，用于下拉框）
     */
    @GetMapping("/admin/list")
    public Result<List<DataSourceVO>> list() {
        return Result.success(dataSourceService.getAllDataSources());
    }

    /**
     * 根据ID获取数据源详情（管理员）
     */
    @GetMapping("/admin/{id}")
    public Result<DataSourceVO> getById(@PathVariable Long id) {
        return Result.success(dataSourceService.getDataSourceById(id));
    }

    /**
     * 测试连接（不入库）
     */
    @PostMapping("/admin/test")
    public Result<String> test(@Valid @RequestBody DataSourceSaveDTO saveDto) {
        dataSourceService.testConnection(saveDto);
        return Result.success("连接测试成功", null);
    }

    /**
     * 新增数据源
     */
    @PostMapping("/admin")
    public Result<Void> save(@Valid @RequestBody DataSourceSaveDTO saveDto) {
        dataSourceService.addDataSource(saveDto);
        return Result.success("数据源添加成功", null);
    }

    /**
     * 修改数据源
     */
    @PutMapping("/admin")
    public Result<Void> update(@Valid @RequestBody DataSourceUpdateDTO updateDto) {
        dataSourceService.updateDataSource(updateDto);
        return Result.success("数据源更新成功", null);
    }

    /**
     * 删除数据源
     */
    @DeleteMapping("/admin/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        dataSourceService.deleteDataSource(id);
        return Result.success("数据源删除成功", null);
    }

    /**
     * 批量删除数据源
     */
    @DeleteMapping("/admin/batch")
    public Result<Void> batchDelete(@RequestBody List<Long> ids) {
        // 参数校验
        if (ids == null || ids.isEmpty()) {
            return Result.error(400, "删除列表不能为空");
        }
        if (ids.size() > 50) {
            return Result.error(400, "单次最多删除50个数据源");
        }
        
        dataSourceService.batchDeleteDataSources(ids);
        return Result.success("批量删除成功", null);
    }

    /**
     * 获取数据源下的所有表名（管理员配置权限时使用）
     */
    @GetMapping("/admin/{id}/tables")
    public Result<List<String>> getTables(@PathVariable Long id) {
        return Result.success(dataSourceService.getTableNames(id));
    }

    /**
     * 获取当前用户有权访问的数据源列表
     */
    @GetMapping("/my")
    @PreAuthorize("hasRole('" + USER + "')")
    public Result<List<DataSourceVO>> getMyDataSources() {
        Long userId = UserContext.getUserId();
        return Result.success(dataSourceService.getMyDataSources(userId));
    }

    /**
     * 获取当前用户有权访问的某个数据源详情
     * （用于验证权限 + 显示详情）
     */
    @GetMapping("/my/{id}")
    @PreAuthorize("hasRole('" + USER + "')")
    public Result<DataSourceVO> getMyDataSourceById(@PathVariable Long id) {
        Long userId = UserContext.getUserId();
        List<DataSourceVO> myDataSources = dataSourceService.getMyDataSources(userId);

        // 校验用户是否有权访问该数据源
        DataSourceVO target = myDataSources.stream()
                .filter(ds -> ds.getId().equals(id))
                .findFirst()
                .orElseThrow(() -> new ServiceException("无权访问该数据源或数据源不存在"));

        return Result.success(target);
    }

    /**
     * 手动刷新数据源表名缓存
     * 当目标库结构发生变更（新增/删除表）后，管理员主动调用此接口
     */
    @PostMapping("/admin/{id}/tables/refresh")
    public Result<List<String>> refreshTables(@PathVariable Long id) {
        return Result.success(dataSourceService.refreshTableNames(id));
    }
}