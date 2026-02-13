package com.xhx.core.service.auth.Impl;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.xhx.common.constant.SecurityConstants;
import com.xhx.common.context.UserContext;
import com.xhx.core.model.LoginUser;
import com.xhx.core.model.dto.LoginDTO;
import com.xhx.core.service.auth.AuthService;
import com.xhx.core.util.JwtUtil;
import com.xhx.dal.entity.QueryPolicy;
import com.xhx.dal.entity.TablePermission;
import com.xhx.dal.entity.User;
import com.xhx.dal.mapper.QueryPolicyMapper;
import com.xhx.dal.mapper.TablePermissionMapper;
import com.xhx.dal.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserMapper userMapper;
    private final TablePermissionMapper tablePermissionMapper;
    private final QueryPolicyMapper queryPolicyMapper;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtUtil jwtUtil;
    private final StringRedisTemplate redisTemplate;

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

        // 将详细权限抓取并缓存到 Redis
        User user = userMapper.selectById(userId);
        refreshUserPermissionsCache(userId, user.getRoleId());

        // 签发轻量级 JWT
        List<String> roles = loginUser.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toList());

        String token = jwtUtil.createToken(userId, loginUser.getUsername(), roles);

        redisTemplate.opsForValue().set("login:token:" + userId, token, 24, TimeUnit.HOURS);

        log.info("==> 用户 {} 登录成功，权限快照已存入 Redis", loginUser.getUsername());
        return token;
    }

    /**
     * 抓取权限并存入 Redis
     */
    private void refreshUserPermissionsCache(Long userId, Long roleId) {
        // 加载表权限
        List<TablePermission> perms = tablePermissionMapper.selectList(
                new LambdaQueryWrapper<TablePermission>().eq(TablePermission::getRoleId, roleId));

        String permKey = SecurityConstants.USER_PERMISSION_KEY + userId;
        redisTemplate.delete(permKey);

        if (!perms.isEmpty()) {
            List<String> permStrings = perms.stream()
                    .map(p -> p.getDataSourceId() + ":" + p.getTableName() + ":" + p.getPermission())
                    .toList();
            redisTemplate.opsForSet().add(permKey, permStrings.toArray(new String[0]));
            redisTemplate.expire(permKey, 24, TimeUnit.HOURS);
        }

        // 加载查询策略
        QueryPolicy policy = queryPolicyMapper.selectOne(
                new LambdaQueryWrapper<QueryPolicy>().eq(QueryPolicy::getRoleId, roleId));

        if (policy != null) {
            String policyKey = SecurityConstants.USER_POLICY_KEY + userId;
            // 将策略对象转为 JSON 存入，方便后续执行 SQL 前校验
            redisTemplate.opsForValue().set(policyKey, JSON.toJSONString(policy), 24, TimeUnit.HOURS);
        }
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
