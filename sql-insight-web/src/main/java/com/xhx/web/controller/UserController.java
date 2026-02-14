package com.xhx.web.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xhx.common.constant.SystemPermissionConstants;
import com.xhx.common.context.UserContext;
import com.xhx.common.result.Result;
import com.xhx.core.model.dto.UserPasswordUpdateDTO;
import com.xhx.core.model.dto.UserSaveDTO;
import com.xhx.core.model.dto.UserUpdateDTO;
import com.xhx.core.model.vo.UserVO;
import com.xhx.core.service.management.UserService;
import com.xhx.dal.entity.User;
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
@PreAuthorize("hasAuthority('" + USER + "')")
public class UserController {

    private final UserService userService;

    /**
     * 分页查询用户
     */
    @GetMapping("/page")
    @PreAuthorize("hasAnyAuthority('" + ADMIN + "')")
    public Result<Page<UserVO>> getUserPage(
            @RequestParam(defaultValue = "1") int current,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String username) {
        Page<UserVO> userPage = userService.getUserPage(current, size, username);
        log.info("用户: {}, 持有原始权限: {}", UserContext.getUser().getUserId(), UserContext.getUser().getRoles());
        return Result.success(userPage);
    }

    /**
     * 获取单个用户详情
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('" + ADMIN + "')")
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
     */
    @PostMapping
    @PreAuthorize("hasAnyAuthority('" + SUPER_ADMIN + "')")
    public Result<Void> saveUser(@RequestBody UserSaveDTO saveDto) {
        userService.saveUser(saveDto);
        return Result.success();
    }

    /**
     * 修改用户（角色、状态等）
     */
    @PutMapping
    @PreAuthorize("hasAnyAuthority('" + ADMIN + "')")
    public Result<Void> updateUser(@RequestBody UserUpdateDTO updateDto) {
        userService.updateUser(updateDto);
        return Result.success();
    }

    /**
     * 用户修改自己的密码
     */
    @PutMapping("/password")
    public Result<Void> updateMyPassword(@RequestBody UserPasswordUpdateDTO passwordDto) {
        Long currentUserId = UserContext.getUserId();
        userService.updateMyPassword(currentUserId, passwordDto);
        return Result.success();
    }

    /**
     * 管理员重置他人密码
     */
    @PutMapping("/{id}/password/reset")
    @PreAuthorize("hasAnyAuthority('" + SUPER_ADMIN + "')")
    public Result<Void> resetPassword(@PathVariable Long id) {
        userService.resetPassword(id);
        return Result.success();
    }

    /**
     * 删除用户
     * 权限：仅限 SUPER_ADMIN
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('" + SUPER_ADMIN + "')")
    public Result<Void> deleteUser(@PathVariable Long id) {
        userService.deleteUser(id);
        return Result.success("删除用户成功",null);
    }

    /**
     * 修改用户的系统权限
     */
    @PutMapping("/{id}/system-permission")
    @PreAuthorize("hasAuthority('" + SUPER_ADMIN + "')")
    public Result<Void> updateSystemPermission(
            @PathVariable Long id,
            @RequestParam String systemPermission) {

        userService.updateSystemPermission(id, systemPermission);
        return Result.success("权限已更新，目标用户需重新登录",null);
    }
}