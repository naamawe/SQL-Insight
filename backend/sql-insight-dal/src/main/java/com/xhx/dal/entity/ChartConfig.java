package com.xhx.dal.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 图表配置实体
 *
 * @author master
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName(value = "chart_config", autoResultMap = true)
public class ChartConfig {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 关联查询记录 ID */
    private Long recordId;

    /** 图表类型：bar/line/pie/scatter/table */
    private String type;

    /** X 轴字段名 */
    private String xAxis;

    /** Y 轴字段名列表（JSON 存储） */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> yAxis;

    /** 图表标题 */
    private String title;

    /** 是否用户手动修改过 */
    private Boolean isUserModified;

    private LocalDateTime gmtCreated;

    private LocalDateTime gmtModified;
}