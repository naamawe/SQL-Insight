package com.xhx.web.config;

import com.xhx.common.IgnoreUrlsConfig;
import com.xhx.common.constant.SystemPermissionConstants;
import com.xhx.core.util.JwtUtil;
import com.xhx.web.filter.JwtAuthenticationFilter;
import com.xhx.web.filter.MdcLoggingFilter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.access.expression.method.DefaultMethodSecurityExpressionHandler;
import org.springframework.security.access.expression.method.MethodSecurityExpressionHandler;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.access.hierarchicalroles.RoleHierarchyImpl;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.servlet.HandlerExceptionResolver;

import java.util.Collections;

/**
 * Spring Security 配置
 * @author master
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtUtil jwtUtil;
    private final StringRedisTemplate redisTemplate;
    private final IgnoreUrlsConfig ignoreUrlsConfig;
    private final MdcLoggingFilter mdcLoggingFilter;
    private final HandlerExceptionResolver resolver;

    public SecurityConfig(
            JwtUtil jwtUtil,
            StringRedisTemplate redisTemplate,
            IgnoreUrlsConfig ignoreUrlsConfig,
            MdcLoggingFilter mdcLoggingFilter,
            @Qualifier("handlerExceptionResolver") HandlerExceptionResolver resolver) {
        this.jwtUtil = jwtUtil;
        this.redisTemplate = redisTemplate;
        this.ignoreUrlsConfig = ignoreUrlsConfig;
        this.mdcLoggingFilter = mdcLoggingFilter;
        this.resolver = resolver;
    }
    @Bean
    public JwtAuthenticationFilter jwtAuthenticationFilter() {
        return new JwtAuthenticationFilter(jwtUtil, redisTemplate, ignoreUrlsConfig);
    }

    /**
     * 定义角色继承关系：SUPER_ADMIN → ADMIN → USER
     */
    @Bean
    public RoleHierarchy roleHierarchy() {
        return RoleHierarchyImpl.withDefaultRolePrefix()
                .role(SystemPermissionConstants.SUPER_ADMIN).implies(SystemPermissionConstants.ADMIN)
                .role(SystemPermissionConstants.ADMIN).implies(SystemPermissionConstants.USER)
                .build();
    }

    /**
     * 将 RoleHierarchy 关联到方法级权限表达式处理器
     */
    @Bean
    static MethodSecurityExpressionHandler methodSecurityExpressionHandler(RoleHierarchy roleHierarchy) {
        DefaultMethodSecurityExpressionHandler handler = new DefaultMethodSecurityExpressionHandler();
        handler.setRoleHierarchy(roleHierarchy);
        return handler;
    }

    /**
     * Security 过滤器链配置
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        String[] urls = ignoreUrlsConfig.getHttpUrls().toArray(new String[0]);
        return http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(urls).permitAll()
                        .anyRequest().authenticated()
                )
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, e) ->
                                resolver.resolveException(request, response, null, e))
                        .accessDeniedHandler((request, response, e) ->
                                resolver.resolveException(request, response, null, e))
                )
                .addFilterBefore(jwtAuthenticationFilter(), UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    @Bean
    public FilterRegistrationBean<MdcLoggingFilter> mdcFilterRegistration() {
        FilterRegistrationBean<MdcLoggingFilter> registration = new FilterRegistrationBean<>(mdcLoggingFilter);
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }

    @Bean
    public UrlBasedCorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowCredentials(true);
        config.setAllowedOriginPatterns(Collections.singletonList("*"));
        config.setAllowedMethods(Collections.singletonList("*"));
        config.setAllowedHeaders(Collections.singletonList("*"));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }
}