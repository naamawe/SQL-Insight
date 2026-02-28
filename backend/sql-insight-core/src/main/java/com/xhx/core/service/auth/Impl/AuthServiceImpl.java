package com.xhx.core.service.auth.Impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.xhx.common.constant.SystemPermissionConstants;
import com.xhx.common.context.UserContext;
import com.xhx.common.exception.ServiceException;
import com.xhx.core.model.LoginUser;
import com.xhx.core.model.dto.LoginDTO;
import com.xhx.core.service.cache.CacheService;
import com.xhx.core.service.auth.AuthService;
import com.xhx.core.util.JwtUtil;
import com.xhx.dal.entity.User;
import com.xhx.dal.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

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
    private final CacheService cacheService;

    @Override
    public String login(LoginDTO loginDto) {
        log.info("用户 {} 尝试登录", loginDto.getUsername());

        Authentication auth = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        loginDto.getUsername(), loginDto.getPassword())
        );

        LoginUser loginUser = (LoginUser) auth.getPrincipal();
        Long userId = loginUser.getUserId();
        Long roleId = loginUser.getRoleId();

        String roleAuthority = loginUser.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .findFirst()
                .orElseThrow();

        // 生成 Token
        String token = jwtUtil.createToken(userId, loginUser.getUsername());

        // 一次性写入所有登录态缓存
        cacheService.putToken(userId, token);
        cacheService.putUserSysPerm(userId, roleAuthority);
        cacheService.putUserRoleId(userId, roleId);

        log.info("用户 {} 登录成功", loginUser.getUsername());
        return token;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void register(LoginDTO registerDto) {
        String username = registerDto.getUsername();
        String password = registerDto.getPassword();

        log.info("==> 尝试注册新用户: {}", username);

        User existUser = userMapper.selectOne(
                new QueryWrapper<User>().eq("user_name", username)
        );
        if (existUser != null) {
            log.warn("==> 注册失败：用户名 {} 已存在", username);
            throw new ServiceException("该用户名已被占用");
        }

        String encodedPassword = passwordEncoder.encode(password);

        User newUser = new User();
        newUser.setUserName(username);
        newUser.setPassword(encodedPassword);
        newUser.setSystemPermission(SystemPermissionConstants.USER);
        newUser.setRoleId(2L);
        newUser.setStatus((short) 1);

        userMapper.insert(newUser);

        log.info("==> 用户 {} 注册成功，ID: {}", username, newUser.getId());
    }

    @Override
    public void logout() {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            return;
        }

        cacheService.evictAllUserCache(userId);
        UserContext.clear();
        log.info("用户 {} 已登出", userId);
    }

    @Override
    public Map<String, Object> getCurrentUserInfo() {
        UserContext.LoginUser loginUser = UserContext.getUser();

        Map<String, Object> userInfo = new HashMap<>();
        userInfo.put("userId", loginUser.getUserId());
        userInfo.put("username", loginUser.getUsername());
        userInfo.put("permissions", loginUser.getRoles());

        return userInfo;
    }
}