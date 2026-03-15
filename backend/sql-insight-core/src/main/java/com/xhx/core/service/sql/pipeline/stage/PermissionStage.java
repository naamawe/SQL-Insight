package com.xhx.core.service.sql.pipeline.stage;

import com.xhx.common.exception.ServiceException;
import com.xhx.core.service.cache.PermissionLoader;
import com.xhx.core.service.sql.pipeline.GeneratePipelineContext;
import com.xhx.core.service.sql.pipeline.PipelineStage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

/**
 * Stage 4：权限过滤，解析当前用户在该数据源下有权访问的表
 * @author master
 */
@Component
@RequiredArgsConstructor
public class PermissionStage implements PipelineStage {

    private final PermissionLoader permissionLoader;

    @Override
    public void process(GeneratePipelineContext ctx) {
        Set<String> allPerms = permissionLoader.loadPermissions(ctx.getUserId(), ctx.getRoleId());
        if (allPerms.isEmpty()) {
            throw new ServiceException("您当前没有任何表的访问权限，请联系管理员授权。");
        }
        Long dataSourceId = ctx.getSession().getDataSourceId();
        List<String> allowed = allPerms.stream()
                .filter(p -> p.startsWith(dataSourceId + ":"))
                .map(p -> p.split(":", 3)[1])
                .toList();
        if (allowed.isEmpty()) {
            throw new ServiceException("您在该数据源下没有已授权的表，请联系管理员配置权限。");
        }
        ctx.setAllowedTables(allowed);
    }
}
