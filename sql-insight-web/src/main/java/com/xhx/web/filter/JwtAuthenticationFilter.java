package com.xhx.web.filter;

import com.xhx.common.IgnoreUrlsConfig;
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
import org.slf4j.MDC;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * JWT 认证过滤器
 * @author master
 */
@Slf4j
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final StringRedisTemplate redisTemplate;
    private final IgnoreUrlsConfig ignoreUrlsConfig;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String servletPath = request.getServletPath();
        return ignoreUrlsConfig.getHttpUrls().stream()
                .anyMatch(pattern -> pathMatcher.match(pattern, servletPath));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    @NotNull HttpServletResponse response,
                                    @NotNull FilterChain filterChain)
            throws ServletException, IOException {

        String authHeader = request.getHeader(SecurityConstants.AUTHORIZATION_HEADER);
        if (!StringUtils.hasText(authHeader) || !authHeader.startsWith(SecurityConstants.TOKEN_PREFIX)) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = authHeader.substring(SecurityConstants.TOKEN_PREFIX.length());

        try {
            Claims claims = jwtUtil.parseToken(token);
            Long userId = claims.get("userId", Long.class);
            String username = claims.getSubject();

            // 校验 Redis 中的 Token 是否仍然有效
            String redisTokenKey = SecurityConstants.REDIS_TOKEN_KEY + userId;
            String cachedToken = redisTemplate.opsForValue().get(redisTokenKey);
            if (!StringUtils.hasText(cachedToken) || !cachedToken.equals(token)) {
                log.warn("Token 已失效，用户: {}", username);
                renderError(response, "登录已失效，请重新登录");
                return;
            }

            // 从 Redis 获取实时权限快照
            String redisPermKey = SecurityConstants.USER_SYS_PERM_KEY + userId;
            String latestPerm = redisTemplate.opsForValue().get(redisPermKey);
            if (!StringUtils.hasText(latestPerm)) {
                log.warn("权限快照缺失，用户: {}", username);
                renderError(response, "权限已变更，请重新登录");
                return;
            }

            // 自动续期：剩余时间低于阈值时延长有效期
            Long expire = redisTemplate.getExpire(redisTokenKey, TimeUnit.MINUTES);
            if (expire != null && expire < SecurityConstants.TOKEN_RENEW_THRESHOLD) {
                redisTemplate.expire(redisTokenKey, SecurityConstants.TOKEN_EXPIRE_MINUTES, TimeUnit.MINUTES);
                redisTemplate.expire(redisPermKey, SecurityConstants.TOKEN_EXPIRE_MINUTES, TimeUnit.MINUTES);
                log.info("Token 自动续期，用户: {}", username);
            }

            if (SecurityContextHolder.getContext().getAuthentication() == null) {
                List<String> roles = Collections.singletonList(latestPerm);
                setupSecurityContext(request, userId, username, roles);

                // 认证成功后将 userId 补充进 MDC，后续所有业务日志自动携带
                MDC.put(MdcLoggingFilter.USER_ID_KEY, String.valueOf(userId));

                log.debug("认证成功，用户: {}，权限: {}", username, latestPerm);
            }

            filterChain.doFilter(request, response);

        } catch (Exception e) {
            log.error("JWT 校验异常: {}", e.getMessage());
            renderError(response, "无效的令牌");
        }
    }

    private void setupSecurityContext(HttpServletRequest request, Long userId,
                                      String username, List<String> roles) {
        List<SimpleGrantedAuthority> authorities = roles.stream()
                .map(SimpleGrantedAuthority::new)
                .collect(Collectors.toList());

        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(username, null, authorities);
        auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(auth);

        UserContext.LoginUser lu = new UserContext.LoginUser();
        lu.setUserId(userId);
        lu.setUsername(username);
        lu.setRoles(roles);
        UserContext.setUser(lu);
    }

    private void renderError(HttpServletResponse response, String msg) throws IOException {
        response.setContentType("application/json;charset=UTF-8");
        response.setStatus(HttpServletResponse.SC_OK);
        response.getWriter().write(
                String.format("{\"code\":401,\"message\":\"%s\",\"data\":null}", msg)
        );
    }
}