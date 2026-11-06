package com.example.ticket.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.ticket.common.BizException;
import com.example.ticket.common.ResultCode;
import com.example.ticket.dto.LoginRequest;
import com.example.ticket.entity.User;
import com.example.ticket.mapper.UserMapper;
import com.example.ticket.security.AuthInterceptor;
import com.example.ticket.security.JwtUtil;
import com.example.ticket.security.UserContext;
import com.example.ticket.vo.LoginVO;
import com.example.ticket.vo.UserVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * 认证服务：登录、登出、取当前用户。
 */
@Slf4j
@Service
public class AuthService {

    private final UserMapper userMapper;
    private final BCryptPasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final StringRedisTemplate redis;

    public AuthService(UserMapper userMapper, BCryptPasswordEncoder passwordEncoder,
                       JwtUtil jwtUtil, StringRedisTemplate redis) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
        this.redis = redis;
    }

    /**
     * 登录。
     *
     * <p>用户名不存在和密码错误返回**同一句提示**，这是故意的：
     * 分开提示等于告诉攻击者"这个用户名存在"，可以拿来批量试账号。
     */
    public LoginVO login(LoginRequest req) {
        User user = userMapper.selectOne(
                Wrappers.<User>lambdaQuery().eq(User::getUsername, req.getUsername()));

        if (user == null || !passwordEncoder.matches(req.getPassword(), user.getPassword())) {
            log.warn("登录失败 username={}", req.getUsername());
            throw new BizException(ResultCode.BIZ_ERROR, "用户名或密码错误");
        }

        String token = jwtUtil.generate(user.getId(), user.getUsername(), user.getRole());

        // 存 Redis 的目的不是"记住登录状态"（JWT 自己就能证明），
        // 而是让令牌可撤销：新登录会覆盖旧值，旧令牌立刻失效；登出直接删除
        redis.opsForValue().set(AuthInterceptor.TOKEN_KEY_PREFIX + user.getId(),
                token, jwtUtil.ttl());

        log.info("登录成功 userId={} username={} role={}", user.getId(), user.getUsername(), user.getRole());
        return new LoginVO(token, UserVO.from(user));
    }

    /**
     * 登出：删掉 Redis 里的令牌，即使 token 还没过期也立刻失效。
     */
    public void logout() {
        Long userId = UserContext.userId();
        redis.delete(AuthInterceptor.TOKEN_KEY_PREFIX + userId);
        log.info("登出 userId={}", userId);
    }

    /**
     * 当前登录用户。前端刷新页面后靠它恢复身份，不用把用户信息长期存在浏览器里。
     */
    public UserVO currentUser() {
        User user = userMapper.selectById(UserContext.userId());
        if (user == null) {
            throw new BizException(ResultCode.UNAUTHORIZED, "用户不存在或已删除");
        }
        return UserVO.from(user);
    }
}
