package com.xhx.core.service.security.Impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xhx.core.model.LoginUser;
import com.xhx.dal.entity.Role;
import com.xhx.dal.entity.User;
import com.xhx.dal.mapper.RoleMapper;
import com.xhx.dal.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

/**
 * 身份认证数据加载
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserDetailsServiceImpl implements UserDetailsService {

    private final UserMapper userMapper;
    private final RoleMapper roleMapper;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        // 查询用户信息
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getUserName, username));

        if (user == null) {
            log.warn("登录失败：用户名 {} 不存在", username);
            throw new UsernameNotFoundException("用户名或密码错误");
        }

        // 检查状态
        if (user.getStatus() != null && user.getStatus() == 0) {
            log.warn("登录失败：用户 {} 已被禁用", username);
            throw new UsernameNotFoundException("该账号已被禁用");
        }

        // 获取角色信息
        Role role = roleMapper.selectById(user.getRoleId());
        String roleName = (role != null) ? role.getRoleName().toUpperCase() : "USER";

        // 4. 构建权限集合
        List<SimpleGrantedAuthority> authorities = Collections.singletonList(
                new SimpleGrantedAuthority("ROLE_" + roleName)
        );

        log.info("用户 {} 身份核实成功，准备进行密码比对", username);

        // 返回 LoginUser 对象
        return new LoginUser(
                user.getId(),
                user.getUserName(),
                user.getPassword(),
                authorities
        );
    }
}
