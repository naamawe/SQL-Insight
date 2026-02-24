package com.xhx.web.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xhx.common.context.UserContext;
import com.xhx.common.result.Result;
import com.xhx.core.model.dto.UserPasswordUpdateDTO;
import com.xhx.core.model.dto.UserSaveDTO;
import com.xhx.core.model.dto.UserUpdateDTO;
import com.xhx.core.model.vo.UserVO;
import com.xhx.core.service.management.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import static com.xhx.common.constant.SystemPermissionConstants.*;

/**
 * 用户管理接口
 * @author master
 */
@Slf4j
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@PreAuthorize("hasRole('" + USER + "')")
public class UserController {

    private final UserService userService;

    @GetMapping("/page")
    @PreAuthorize("hasRole('" + ADMIN + "')")
    public Result<Page<UserVO>> getUserPage(
            @RequestParam(defaultValue = "1") int current,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String username) {
        return Result.success(userService.getUserPage(current, size, username));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('" + ADMIN + "')")
    public Result<UserVO> getUserById(@PathVariable Long id) {
        return Result.success(userService.getUserById(id));
    }

    @GetMapping("/me")
    public Result<UserVO> getCurrentUserInfo() {
        return Result.success(userService.getUserById(UserContext.getUserId()));
    }

    @PostMapping
    @PreAuthorize("hasRole('" + SUPER_ADMIN + "')")
    public Result<Void> saveUser(@Valid @RequestBody UserSaveDTO saveDto) {
        userService.saveUser(saveDto);
        return Result.success();
    }

    @PutMapping
    @PreAuthorize("hasRole('" + ADMIN + "')")
    public Result<Void> updateUser(@Valid @RequestBody UserUpdateDTO updateDto) {
        userService.updateUser(updateDto);
        return Result.success();
    }

    @PutMapping("/password")
    public Result<Void> updateMyPassword(@Valid @RequestBody UserPasswordUpdateDTO passwordDto) {
        userService.updateMyPassword(UserContext.getUserId(), passwordDto);
        return Result.success();
    }

    @PutMapping("/{id}/password/reset")
    @PreAuthorize("hasRole('" + SUPER_ADMIN + "')")
    public Result<Void> resetPassword(@PathVariable Long id) {
        userService.resetPassword(id);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('" + SUPER_ADMIN + "')")
    public Result<Void> deleteUser(@PathVariable Long id) {
        userService.deleteUser(id);
        return Result.success("删除用户成功", null);
    }

    @PutMapping("/{id}/system-permission")
    @PreAuthorize("hasRole('" + SUPER_ADMIN + "')")
    public Result<Void> updateSystemPermission(
            @PathVariable Long id,
            @RequestParam String systemPermission) {
        userService.updateSystemPermission(id, systemPermission);
        return Result.success("权限已更新，目标用户需重新登录", null);
    }
}