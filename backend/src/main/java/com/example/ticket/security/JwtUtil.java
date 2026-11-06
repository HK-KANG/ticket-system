package com.example.ticket.security;

import com.example.ticket.common.AuthException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Date;

/**
 * JWT 签发与解析。
 *
 * <p>令牌里只放三样东西：userId（sub）、username、role。
 * 权限和数据范围仍然查数据库或 Redis，**不要把权限清单塞进 token**——
 * 那样改权限就得等 token 过期才生效。
 *
 * <p>密钥从环境变量 TICKET_JWT_SECRET 读，默认值只能用于本地。
 * HS256 要求密钥至少 32 字节（256 位），短了 jjwt 会直接抛异常。
 */
@Component
public class JwtUtil {

    private final SecretKey key;

    /** 令牌有效期（分钟），与 Redis 里的 TTL 保持一致 */
    private final long expireMinutes;

    public JwtUtil(@Value("${ticket.jwt.secret}") String secret,
                   @Value("${ticket.jwt.expire-minutes}") long expireMinutes) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expireMinutes = expireMinutes;
    }

    /**
     * 签发令牌。
     */
    public String generate(Long userId, String username, String role) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + Duration.ofMinutes(expireMinutes).toMillis());
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("username", username)
                .claim("role", role)
                .issuedAt(now)
                .expiration(expiry)
                .signWith(key)
                .compact();
    }

    /**
     * 解析令牌。签名不对、格式不对、已过期都会抛 {@link AuthException}，
     * 交给全局异常处理器转成 401，不要在拦截器里各写一套。
     */
    public LoginUser parse(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return new LoginUser(
                    Long.valueOf(claims.getSubject()),
                    claims.get("username", String.class),
                    claims.get("role", String.class));
        } catch (ExpiredJwtException e) {
            throw new AuthException("登录已过期，请重新登录");
        } catch (JwtException | IllegalArgumentException e) {
            throw new AuthException("登录凭证无效");
        }
    }

    /**
     * 令牌有效期，供 Redis 设置同样的 TTL。
     */
    public Duration ttl() {
        return Duration.ofMinutes(expireMinutes);
    }
}
