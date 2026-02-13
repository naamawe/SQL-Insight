package com.xhx.web.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xhx.common.context.UserContext;
import com.xhx.common.result.Result;
import com.xhx.core.model.dto.UserPasswordUpdateDTO;
import com.xhx.core.model.dto.UserSaveDTO;
import com.xhx.core.model.dto.UserUpdateDTO;
import com.xhx.core.model.vo.UserVO;
import com.xhx.core.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * 用户管理接口
 * @author master
 */
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    /**
     * 分页查询用户
     * 权限：ADMIN 或 SUPER_ADMIN
     */
    @GetMapping("/page")
    @PreAuthorize("hasAuthority('ADMIN') or hasAuthority('SUPER_ADMIN')")
    public Result<Page<UserVO>> getUserPage(
            @RequestParam(defaultValue = "1") int current,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String username) {
        return Result.success(userService.getUserPage(current, size, username));
    }

    /**
     * 获取单个用户详情
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('USER')")
    public Result<UserVO> getUserById(@PathVariable Long id) {
        return Result.success(userService.getUserById(id));
    }

    /**
     * 获取当前登录用户自己的信息
     */
    @GetMapping("/me")
    public Result<UserVO> getCurrentUserInfo() {
        Long currentUserId = UserContext.getUserId();
        return Result.success(userService.getUserById(currentUserId));
    }

    /**
     * 新增用户
     * 权限：仅限 SUPER_ADMIN
     */
    @PostMapping
    @PreAuthorize("hasAuthority('SUPER_ADMIN')")
    public Result<Void> saveUser(@RequestBody UserSaveDTO saveDto) {
        userService.saveUser(saveDto);
        return Result.success();
    }

    /**
     * 修改用户（角色、状态等）
     * 权限：ADMIN 或 SUPER_ADMIN
     */
    @PutMapping
    @PreAuthorize("hasAuthority('ADMIN') or hasAuthority('SUPER_ADMIN')")
    public Result<Void> updateUser(@RequestBody UserUpdateDTO updateDto) {
        userService.updateUser(updateDto);
        return Result.success();
    }

    /**
     * 用户修改自己的密码
     * 逻辑：通过 UserContext 确定身份，无需前端传 userId
     */
    @PutMapping("/password")
    public Result<Void> updateMyPassword(@RequestBody UserPasswordUpdateDTO passwordDto) {
        Long currentUserId = UserContext.getUserId();
        userService.updateMyPassword(currentUserId, passwordDto);
        return Result.success();
    }

    /**
     * 管理员重置他人密码
     * 权限：仅限 SUPER_ADMIN
     */
    @PutMapping("/{id}/password/reset")
    @PreAuthorize("hasAuthority('SUPER_ADMIN')")
    public Result<Void> resetPassword(@PathVariable Long id) {
        userService.resetPassword(id);
        return Result.success();
    }

    /**
     * 删除用户
     * 权限：仅限 SUPER_ADMIN
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('SUPER_ADMIN')")
    public Result<Void> deleteUser(@PathVariable Long id) {
        userService.deleteUser(id);
        return Result.success();
    }
}