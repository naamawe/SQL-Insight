package com.xhx.core.service.security.Impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xhx.common.constant.SystemPermissionConstants;
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
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * 身份认证数据加载
 * @author master
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserDetailsServiceImpl implements UserDetailsService {

    private final UserMapper userMapper;
    private final RoleMapper roleMapper;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getUserName, username));
        if (user == null) {
            throw new UsernameNotFoundException("用户名或密码错误");
        }

        if (user.getStatus() != null && user.getStatus() == 0) {
            throw new UsernameNotFoundException("该账号已被禁用");
        }

        Role role = roleMapper.selectById(user.getRoleId());

        // 确定系统权限标识
        String sysPerm;
        if (role != null && StringUtils.hasText(role.getSystemPermission())) {
            sysPerm = role.getSystemPermission().toUpperCase();
        } else {
            sysPerm = SystemPermissionConstants.USER;
        }

        // 构建 Authority 集合
        List<SimpleGrantedAuthority> authorities = new ArrayList<>();
        authorities.add(new SimpleGrantedAuthority(sysPerm));

        log.info("用户 {} 登录，系统权限标记: {}", username, sysPerm);

        return new LoginUser(
                user.getId(),
                user.getRoleId(),
                user.getUserName(),
                user.getPassword(),
                authorities
        );
    }
}
