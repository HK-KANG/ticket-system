package com.example.ticket.controller;

import com.example.ticket.common.Result;
import com.example.ticket.dto.LoginRequest;
import com.example.ticket.service.AuthService;
import com.example.ticket.vo.LoginVO;
import com.example.ticket.vo.UserVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证接口。
 *
 * <p>只有 /api/auth/login 是匿名可访问的（在 WebMvcConfig 白名单里），
 * 其余两个需要带 token。
 */
@Tag(name = "认证", description = "登录、登出、当前用户")
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /**
     * 登录并签发令牌。
     */
    @Operation(summary = "登录", description = "返回 JWT 令牌和用户信息；用户名或密码错误统一提示")
    @PostMapping("/login")
    public Result<LoginVO> login(@Valid @RequestBody LoginRequest req) {
        return Result.ok(authService.login(req));
    }

    /**
     * 登出，令牌立即失效。
     */
    @Operation(summary = "登出", description = "删除 Redis 中的令牌，未过期的 token 也不能再用")
    @PostMapping("/logout")
    public Result<Void> logout() {
        authService.logout();
        return Result.ok();
    }

    /**
     * 当前登录用户。
     */
    @Operation(summary = "当前用户", description = "前端刷新页面后用它恢复登录态")
    @GetMapping("/me")
    public Result<UserVO> me() {
        return Result.ok(authService.currentUser());
    }
}
