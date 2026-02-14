package com.xhx.core.service.management.Impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xhx.common.constant.SecurityConstants;
import com.xhx.core.model.dto.UserPasswordUpdateDTO;
import com.xhx.core.model.dto.UserSaveDTO;
import com.xhx.core.model.dto.UserUpdateDTO;
import com.xhx.core.model.vo.UserVO;
import com.xhx.core.service.management.UserService;
import com.xhx.dal.entity.Role;
import com.xhx.dal.entity.User;
import com.xhx.dal.entity.UserDataSource;
import com.xhx.dal.mapper.RoleMapper;
import com.xhx.dal.mapper.UserDataSourceMapper;
import com.xhx.dal.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author master
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserMapper userMapper;
    private final RoleMapper roleMapper;
    private final UserDataSourceMapper userDataSourceMapper;
    private final PasswordEncoder passwordEncoder;
    private final StringRedisTemplate redisTemplate;

    @Value("${defaultPassword}")
    private String defaultPassword;

    /**
     * 分页查询
     */
    @Override
    public Page<UserVO> getUserPage(int current, int size, String username) {
        Page<User> page = new Page<>(current, size);
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(username)) {
            wrapper.like(User::getUserName, username);
        }
        // 排除敏感字段：密码
        wrapper.select(User.class, info -> !"password".equals(info.getColumn()));
        userMapper.selectPage(page, wrapper);

        if (page.getRecords().isEmpty()) {
            return new Page<>(current, size, 0);
        }

        // 批量查询角色，避免在循环中查库
        Set<Long> roleIds = page.getRecords().stream()
                .map(User::getRoleId)
                .collect(Collectors.toSet());
        Map<Long, String> roleMap = roleMapper.selectBatchIds(roleIds).stream()
                .collect(Collectors.toMap(Role::getId, Role::getRoleName));

        List<UserVO> voList = page.getRecords().stream().map(user -> {
            UserVO vo = new UserVO();
            BeanUtils.copyProperties(user, vo);
            vo.setRoleName(roleMap.getOrDefault(user.getRoleId(), "未知角色"));
            return vo;
        }).collect(Collectors.toList());

        Page<UserVO> voPage = new Page<>(current, size, page.getTotal());
        voPage.setRecords(voList);
        return voPage;
    }

    /**
     * 获取详情
     */
    @Override
    public UserVO getUserById(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }

        UserVO vo = new UserVO();
        BeanUtils.copyProperties(user, vo);
        Role role = roleMapper.selectById(user.getRoleId());
        if (role != null) {
            vo.setRoleName(role.getRoleName());
        }
        return vo;
    }

    /**
     * 新增用户：包含用户名重复校验和加密
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveUser(UserSaveDTO saveDto) {
        // 唯一性校验
        Long count = userMapper.selectCount(new LambdaQueryWrapper<User>()
                .eq(User::getUserName, saveDto.getUserName()));
        if (count > 0) {
            throw new RuntimeException("用户名已存在");
        }

        // 转换并加密
        User user = new User();
        BeanUtils.copyProperties(saveDto, user);
        user.setPassword(passwordEncoder.encode(saveDto.getPassword()));
        user.setStatus((short) 1);
        userMapper.insert(user);
    }

    /**
     * 修改用户：包含状态变更处理和缓存清理
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateUser(UserUpdateDTO updateDto) {
        User oldUser = userMapper.selectById(updateDto.getId());
        if (oldUser == null) {
            throw new RuntimeException("用户不存在");
        }

        // 禁止禁用超级管理员
        if ("admin".equals(oldUser.getUserName()) && updateDto.getStatus() == 0) {
            throw new RuntimeException("系统核心管理员不可禁用");
        }

        // 如果修改了角色或状态，执行踢出逻辑（确保权限实时生效）
        boolean needKickOut = !oldUser.getRoleId().equals(updateDto.getRoleId())
                || !oldUser.getStatus().equals(updateDto.getStatus());

        BeanUtils.copyProperties(updateDto, oldUser);
        userMapper.updateById(oldUser);

        if (needKickOut) {
            kickOutUser(updateDto.getId());
        }
    }

    /**
     * 删除用户：级联清理
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteUser(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            return;
        }

        if ("admin".equals(user.getUserName())) {
            throw new RuntimeException("不能删除系统初始化管理员");
        }

        // 踢出登录状态
        kickOutUser(userId);

        // 级联软删除关联权限
        userDataSourceMapper.delete(new LambdaQueryWrapper<UserDataSource>()
                .eq(UserDataSource::getUserId, userId));

        // 释放用户名
        user.setUserName(user.getUserName() + "_del_" + System.currentTimeMillis());
        userMapper.updateById(user);

        // 执行逻辑删除
        userMapper.deleteById(userId);
    }

    /**
     * 修改密码
     * @param userId 用户 id
     * @param passwordDto 密码信息
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateMyPassword(Long userId, UserPasswordUpdateDTO passwordDto) {
        // 获取用户信息
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }

        // 校验原密码是否正确
        if (!passwordEncoder.matches(passwordDto.getOldPassword(), user.getPassword())) {
            throw new RuntimeException("原密码错误，请重新输入");
        }

        // 校验两次新密码是否一致（前端通常会做，后端必须双重校验）
        if (!passwordDto.getNewPassword().equals(passwordDto.getConfirmPassword())) {
            throw new RuntimeException("两次输入的新密码不一致");
        }

        // 加密新密码并保存
        user.setPassword(passwordEncoder.encode(passwordDto.getNewPassword()));
        userMapper.updateById(user);

        // 修改密码后必须执行下线操作，要求用户重新登录，确保安全
        log.info("用户 {} 修改了密码，执行强制下线", userId);
        kickOutUser(userId);
    }

    /**
     * 管理员重置密码
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void resetPassword(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }

        user.setPassword(passwordEncoder.encode(defaultPassword));
        userMapper.updateById(user);

        // 密码修改后强制下线，防止盗号者继续持有旧Token操作
        kickOutUser(userId);
    }

    /**
     * 核心安全逻辑：清理 Redis 中该用户的所有相关缓存
     */
    private void kickOutUser(Long userId) {
        log.info("==> 执行安全清理，用户 ID: {}", userId);
        // 清理 Token 认证信息
        redisTemplate.delete(SecurityConstants.REDIS_TOKEN_KEY + userId);
        // 清理该用户的权限快照（关键：防止 AI 生成 SQL 时引用旧权限）
        redisTemplate.delete(SecurityConstants.USER_PERMISSION_KEY + userId);
        // 如果有其他缓存策略同步清理
        redisTemplate.delete(SecurityConstants.USER_POLICY_KEY + userId);
    }
}
