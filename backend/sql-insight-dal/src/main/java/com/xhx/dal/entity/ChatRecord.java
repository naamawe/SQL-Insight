package com.xhx.dal.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * @author master
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("chat_record")
public class ChatRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long sessionId;

    private String question;

    private String sqlText;

    private String summary;

    /** 实际查询总行数 */
    private Integer rowTotal;

    /** 是否经过自动纠错：0-否，1-是 */
    private Short corrected;

    private LocalDateTime createTime;
}