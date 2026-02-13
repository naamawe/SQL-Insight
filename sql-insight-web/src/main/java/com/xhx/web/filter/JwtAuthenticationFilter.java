package com.xhx.web.filter;

import com.xhx.common.constant.SecurityConstants;
import com.xhx.common.context.UserContext;
import com.xhx.core.util.JwtUtil;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final StringRedisTemplate redisTemplate;

    @Override
    protected void doFilterInternal(HttpServletRequest request, @NotNull HttpServletResponse response, @NotNull FilterChain filterChain)
            throws ServletException, IOException {

        String authHeader = request.getHeader(SecurityConstants.AUTHORIZATION_HEADER);
        if (!StringUtils.hasText(authHeader) || !authHeader.startsWith(SecurityConstants.TOKEN_PREFIX)) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = authHeader.substring(SecurityConstants.TOKEN_PREFIX.length());

        try {
            // JWT 解析
            Claims claims = jwtUtil.parseToken(token);
            Long userId = claims.get("userId", Long.class);
            String username = claims.getSubject();

            // Redis 校验
            String redisKey = SecurityConstants.REDIS_TOKEN_KEY + userId;
            String cachedToken = redisTemplate.opsForValue().get(redisKey);

            if (!StringUtils.hasText(cachedToken)) {
                log.warn("拦截请求：用户 {} 的 Token 在 Redis 中已过期或不存在", username);
                filterChain.doFilter(request, response);
                return;
            }

            if (!cachedToken.equals(token)) {
                log.warn("拦截请求：用户 {} 的 Token 与 Redis 不匹配（可能在别处登录，已被踢下线）", username);
                filterChain.doFilter(request, response);
                return;
            }

            // 3. 自动续期
            Long expire = redisTemplate.getExpire(redisKey, TimeUnit.MINUTES);
            if (expire < SecurityConstants.TOKEN_RENEW_THRESHOLD) {
                redisTemplate.expire(redisKey, SecurityConstants.TOKEN_EXPIRE_MINUTES, TimeUnit.MINUTES);
                log.info("自动续期：用户 {} 的 Token 剩余 {} 分钟，已触发续期", username, expire);
            }

            // 填充上下文
            if (SecurityContextHolder.getContext().getAuthentication() == null) {
                @SuppressWarnings("unchecked")
                List<String> roles = (List<String>) claims.get("roles", List.class);
                setupSecurityContext(request, userId, username, roles);
                log.debug("身份验证成功：用户 {}，角色 {}", username, roles);
            }

        } catch (Exception e) {
            log.error("Token 校验异常 [{}]: {}", request.getRequestURI(), e.getMessage());
        }

        try {
            filterChain.doFilter(request, response);
        } finally {
            UserContext.clear();
        }
    }

    private void setupSecurityContext(HttpServletRequest request, Long userId, String username, List<String> roles) {
        List<SimpleGrantedAuthority> authorities = roles.stream()
                .map(SimpleGrantedAuthority::new).collect(Collectors.toList());
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(username, null, authorities);
        auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(auth);

        UserContext.LoginUser lu = new UserContext.LoginUser();
        lu.setUserId(userId);
        lu.setUsername(username);
        lu.setRoles(roles);
        UserContext.setUser(lu);
    }
}
