package com.example.ticket.controller;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.ticket.common.RequireRole;
import com.example.ticket.common.Result;
import com.example.ticket.entity.User;
import com.example.ticket.mapper.UserMapper;
import com.example.ticket.vo.UserVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 用户查询接口。
 *
 * <p>目前只给管理员用——它同时是 {@link RequireRole} 的第一个真实使用场景：
 * 客户或坐席调用会拿到 403，管理员才能拿到列表。
 * 完整的用户管理（增删改、重置密码）排在后面的阶段。
 */
@Tag(name = "用户", description = "用户查询（管理员）")
@RestController
@RequestMapping("/api/user")
public class UserController {

    private final UserMapper userMapper;

    public UserController(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    /**
     * 全部用户列表，仅管理员可见。
     */
    @Operation(summary = "用户列表", description = "仅 ADMIN 可访问，其他角色返回 403")
    @RequireRole("ADMIN")
    @GetMapping("/list")
    public Result<List<UserVO>> list() {
        List<User> users = userMapper.selectList(
                Wrappers.<User>lambdaQuery().orderByAsc(User::getId));
        return Result.ok(users.stream().map(UserVO::from).toList());
    }
}
