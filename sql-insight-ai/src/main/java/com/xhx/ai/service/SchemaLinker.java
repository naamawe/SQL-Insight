package com.xhx.ai.service;

import com.xhx.common.model.TableMetadata;

import java.util.List;

/**
 * @author master
 */
public interface SchemaLinker {
    List<TableMetadata> link(String question, List<TableMetadata> candidates);
}