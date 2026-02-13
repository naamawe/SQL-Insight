package com.xhx.core.service.Impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xhx.common.constant.SecurityConstants;
import com.xhx.core.model.dto.UserSaveDTO;
import com.xhx.core.model.dto.UserUpdateDTO;
import com.xhx.core.model.vo.UserVO;
import com.xhx.core.service.UserService;
import com.xhx.dal.entity.Role;
import com.xhx.dal.entity.User;
import com.xhx.dal.mapper.RoleMapper;
import com.xhx.dal.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserMapper userMapper;
    private final RoleMapper roleMapper;
    private final PasswordEncoder passwordEncoder;
    private final StringRedisTemplate redisTemplate;

    @Override
    public Page<UserVO> getUserPage(int current, int size, String username) {
        // 分页查询用户实体
        Page<User> page = new Page<>(current, size);
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(username)) {
            wrapper.like(User::getUserName, username);
        }
        userMapper.selectPage(page, wrapper);

        // 将 Entity 转换为 VO，并填充角色名
        List<UserVO> voList = page.getRecords().stream().map(user -> {
            UserVO vo = new UserVO();
            BeanUtils.copyProperties(user, vo);

            // 获取角色信息
            Role role = roleMapper.selectById(user.getRoleId());
            if (role != null) {
                vo.setRoleName(role.getRoleName());
            }
            return vo;
        }).collect(Collectors.toList());

        // 封装分页结果
        Page<UserVO> voPage = new Page<>(current, size, page.getTotal());
        voPage.setRecords(voList);
        return voPage;
    }

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


    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveUser(UserSaveDTO saveDto) {
        // 检查用户名是否重复
        long count = userMapper.selectCount(new LambdaQueryWrapper<User>()
                .eq(User::getUserName, saveDto.getUserName()));
        if (count > 0) {
            throw new RuntimeException("用户名 [" + saveDto.getUserName() + "] 已存在");
        }

        User user = new User();
        BeanUtils.copyProperties(saveDto, user);
        // 密码加密存储
        user.setPassword(passwordEncoder.encode(saveDto.getPassword()));
        userMapper.insert(user);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateUser(UserUpdateDTO updateDto) {
        User user = userMapper.selectById(updateDto.getId());
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }

        if (updateDto.getStatus() != null && updateDto.getStatus() == 0) {
            kickOutUser(updateDto.getId());
        }

        BeanUtils.copyProperties(updateDto, user);
        userMapper.updateById(user);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteUser(Long userId) {
        kickOutUser(userId);
        userMapper.deleteById(userId);
    }

    /**
     * 核心安全逻辑：清理 Redis 中该用户的所有相关缓存
     * 作用：实现“物理下线”，使其即便持有 JWT 也无法通过 SQL 安全校验
     */
    private void kickOutUser(Long userId) {
        log.info("==> 正在执行踢出操作，清理用户 ID: {} 的所有安全上下文", userId);

        // 1. 清理 Token 状态
        redisTemplate.delete(SecurityConstants.REDIS_TOKEN_KEY + userId);

        // 2. 清理表权限快照
        redisTemplate.delete(SecurityConstants.USER_PERMISSION_KEY + userId);

        // 3. 清理查询策略
        redisTemplate.delete(SecurityConstants.USER_POLICY_KEY + userId);
    }
}
