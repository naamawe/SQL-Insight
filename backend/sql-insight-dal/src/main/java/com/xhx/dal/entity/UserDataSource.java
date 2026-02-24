package com.xhx.dal.entity;

import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * @author master
 */
@Data
@TableName("user_data_source")
public class UserDataSource {
    private Long userId;
    private Long dataSourceId;
}
