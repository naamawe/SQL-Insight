package com.xhx.core.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xhx.core.model.dto.UserPasswordUpdateDTO;
import com.xhx.core.model.dto.UserSaveDTO;
import com.xhx.core.model.dto.UserUpdateDTO;
import com.xhx.core.model.vo.UserVO;

/**
 * @author master
 */
public interface UserService {
    /**
     * 获取用户分页
     * @param current 目前页数
     * @param size 每页大小
     * @param username 用户名
     * @return 用户分页
     */
    Page<UserVO> getUserPage(int current, int size, String username);

    /**
     * 根据 ID 获取用户详情
     * @param userId 用户 id
     * @return 用户详情
     */
    UserVO getUserById(Long userId);

    /**
     * 新增用户
     * @param saveDto 用户信息
     */
    void saveUser(UserSaveDTO saveDto);

    /**
     * 更新用户
     * @param updateDto 用户信息
     */
    void updateUser(UserUpdateDTO updateDto);

    /**
     * 删除用户
     * @param userId 用户 id
     */
    void deleteUser(Long userId);

    /**
     * 修改密码
     * @param userId 用户 id
     * @param passwordDto 密码信息
     */
    void updateMyPassword(Long userId, UserPasswordUpdateDTO passwordDto);

    /**
     * 重置密码
     * @param userId 用户 id
     */
    void resetPassword(Long userId);
}
