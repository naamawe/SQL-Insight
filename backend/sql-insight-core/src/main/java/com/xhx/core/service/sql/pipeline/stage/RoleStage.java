package com.xhx.core.service.sql.pipeline.stage;

import com.xhx.common.exception.NotExistException;
import com.xhx.core.service.cache.CacheService;
import com.xhx.core.service.sql.pipeline.GeneratePipelineContext;
import com.xhx.core.service.sql.pipeline.PipelineStage;
import com.xhx.dal.entity.User;
import com.xhx.dal.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Stage 3：解析用户角色 ID（优先走缓存）
 * @author master
 */
@Component
@RequiredArgsConstructor
public class RoleStage implements PipelineStage {

    private final CacheService cacheService;
    private final UserMapper userMapper;

    @Override
    public void process(GeneratePipelineContext ctx) {
        Long userId = ctx.getUserId();
        Long roleId = cacheService.getUserRoleId(userId);
        if (roleId == null) {
            User user = userMapper.selectById(userId);
            if (user == null) {
                throw new NotExistException(404, "用户不存在");
            }
            roleId = user.getRoleId();
            cacheService.putUserRoleId(userId, roleId);
        }
        ctx.setRoleId(roleId);
    }
}
