package com.xhx.ai.service;

import com.xhx.common.model.TableMetadata;

import java.util.List;

/**
 * Schema Linking 策略接口
 * <p>
 * 根据用户问题，从候选表中筛选语义最相关的子集，
 * 减少注入 Prompt 的 Schema 噪音，降低 token 消耗和幻觉风险。
 * <p><b>实现契约：</b>
 * <ul>
 *   <li>永远不返回 null，candidates 为空时直接返回空列表</li>
 *   <li>不修改 candidates 列表本身（只做过滤，不排序变形）</li>
 *   <li>实现类必须自行兜底，不允许将异常抛到调用链</li>
 * </ul>
 *
 * @author master
 */
public interface SchemaLinker {

    /**
     * 从候选表中筛选与用户问题最相关的表
     *
     * @param question     用户原始问题
     * @param dataSourceId 当前操作的数据源 ID（向量检索时用于数据源隔离）
     * @param candidates   当前用户有权限访问的所有表元数据
     * @return 过滤后的相关表列表，不为 null
     */
    List<TableMetadata> link(String question, Long dataSourceId, List<TableMetadata> candidates);
}