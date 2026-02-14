package com.xhx.core.service.auth.Impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.xhx.common.constant.SecurityConstants;
import com.xhx.common.context.UserContext;
import com.xhx.core.model.LoginUser;
import com.xhx.core.model.dto.LoginDTO;
import com.xhx.core.service.management.RolePermissionService;
import com.xhx.core.service.auth.AuthService;
import com.xhx.core.util.JwtUtil;
import com.xhx.dal.entity.User;
import com.xhx.dal.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.TimeUnit;

/**
 * @author master
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtUtil jwtUtil;
    private final StringRedisTemplate redisTemplate;
    private final RolePermissionService rolePermissionService;

    @Override
    public String login(LoginDTO loginDto) {
        log.info("==> 用户 {} 尝试登录", loginDto.getUsername());

        // Spring Security 认证
        Authentication auth = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(loginDto.getUsername(), loginDto.getPassword())
        );

        // 认证成功，获取 LoginUser
        LoginUser loginUser = (LoginUser) auth.getPrincipal();

        Long userId = loginUser.getUserId();
        Long roleId = loginUser.getRoleId();

        String systemPerm = loginUser.getAuthorities().iterator().next().getAuthority();

        redisTemplate.opsForValue().set(
                SecurityConstants.USER_SYS_PERM_KEY + userId,
                systemPerm,
                24, TimeUnit.HOURS
        );

        rolePermissionService.refreshUserPermissionsCache(userId, roleId);

        // 签发轻量级 JWT
        String token = jwtUtil.createToken(userId, loginUser.getUsername());

        redisTemplate.opsForValue().set("login:token:" + userId, token, 24, TimeUnit.HOURS);

        log.info("==> 用户 {} 登录成功，权限快照已存入 Redis", loginUser.getUsername());
        return token;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void register(LoginDTO registerDto) {
        String username = registerDto.getUsername();
        String password = registerDto.getPassword();

        log.info("==> 尝试注册新用户: {}", username);

        // 校验用户名是否已存在
        User existUser = userMapper.selectOne(new QueryWrapper<User>().eq("user_name", username));
        if (existUser != null) {
            log.warn("==> 注册失败：用户名 {} 已存在", username);
            throw new RuntimeException("该用户名已被占用");
        }

        // 加密密码
        String encodedPassword = passwordEncoder.encode(password);

        // 构建用户对象并保存
        User newUser = new User();
        newUser.setUserName(username);
        newUser.setPassword(encodedPassword);
        newUser.setStatus((short) 1);
        // 新注册默认用户
        newUser.setRoleId(2L);

        userMapper.insert(newUser);

        log.info("==> 用户 {} 注册成功，ID 为: {}", username, newUser.getId());
    }

    @Override
    public void logout() {
        // 退出登录日志
        log.info("==> 用户 {} 请求退出登录", UserContext.getUserId());
        String redisKey = SecurityConstants.REDIS_TOKEN_KEY + UserContext.getUserId();
        redisTemplate.delete(redisKey);
    }
}
