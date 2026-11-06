package com.example.ticket.security;

import com.example.ticket.common.AuthException;
import com.example.ticket.common.ForbiddenException;
import com.example.ticket.common.RequireRole;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Arrays;

/**
 * 登录态与角色校验拦截器。
 *
 * <p>只做三件事，顺序不能乱：
 * <ol>
 *   <li>解析 Authorization 头里的 JWT —— 不合法直接 401</li>
 *   <li>比对 Redis 里的令牌 —— 不一致说明已被踢下线或重新登录过，也 401</li>
 *   <li>检查 {@link RequireRole} 注解 —— 角色不匹配返回 403</li>
 * </ol>
 *
 * <p>关于第 2 步：JWT 本身无状态，签出去就收不回来。
 * 把令牌同时写进 Redis，是为了支持「踢下线」和「重新登录使旧令牌失效」——
 * 这是拿一个 Redis 读换取可撤销的能力，代价是一次 GET。
 * 如果哪天完全不需要撤销，把这一步去掉，系统照样能跑。
 */
@Component
public class AuthInterceptor implements HandlerInterceptor {

    /** Redis key 前缀：auth:token:{userId} */
    public static final String TOKEN_KEY_PREFIX = "auth:token:";

    private static final String HEADER = "Authorization";
    private static final String BEARER = "Bearer ";

    private final JwtUtil jwtUtil;
    private final StringRedisTemplate redis;

    public AuthInterceptor(JwtUtil jwtUtil, StringRedisTemplate redis) {
        this.jwtUtil = jwtUtil;
        this.redis = redis;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // 静态资源、错误页等非 Controller 方法直接放行
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }

        String header = request.getHeader(HEADER);
        if (header == null || !header.startsWith(BEARER)) {
            throw new AuthException();
        }

        String token = header.substring(BEARER.length()).trim();
        LoginUser user = jwtUtil.parse(token);

        // 比对 Redis：令牌被顶掉（重新登录）或已登出时，这里对不上
        String cached = redis.opsForValue().get(TOKEN_KEY_PREFIX + user.userId());
        if (cached == null || !cached.equals(token)) {
            throw new AuthException("登录已失效，请重新登录");
        }

        UserContext.set(user);
        checkRole(handlerMethod, user);
        return true;
    }

    /**
     * 请求结束务必清理 ThreadLocal，否则线程复用时会把上一个人的身份带给下一个人。
     */
    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        UserContext.clear();
    }

    /**
     * 角色校验：方法上的注解优先于类上的注解。
     */
    private void checkRole(HandlerMethod handlerMethod, LoginUser user) {
        RequireRole required = handlerMethod.getMethodAnnotation(RequireRole.class);
        if (required == null) {
            required = handlerMethod.getBeanType().getAnnotation(RequireRole.class);
        }
        if (required == null) {
            return;
        }
        boolean allowed = Arrays.asList(required.value()).contains(user.role());
        if (!allowed) {
            throw new ForbiddenException("该操作仅限 " + String.join("/", required.value()) + " 使用");
        }
    }
}
