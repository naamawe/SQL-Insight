package com.xhx.core.cache;

import com.xhx.ai.service.SchemaIndexingService;
import com.xhx.core.service.sql.SchemaIndexingFacade;
import com.xhx.dal.entity.DataSource;
import com.xhx.dal.mapper.DataSourceMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 向量索引启动预热
 *
 * <p>系统启动后按序完成：
 * <ol>
 *   <li>确保 Qdrant Collection 存在（首次部署自动建库，幂等）</li>
 *   <li>对所有现有数据源全量重建向量索引（处理存量数据 / 重启恢复）</li>
 * </ol>
 *
 * <p><b>Order(20)：</b>在 PermissionCacheWarmUp（默认 Order=0）之后执行，
 * 避免两个预热任务同时对 DB 和外部服务施加并发压力。
 *
 * <p><b>为什么用同步 rebuildBatch 而不是 rebuildAsync：</b><br>
 * 启动预热需要保证服务完全就绪前 Qdrant 已有数据，
 * 否则冷启动初期所有向量检索都会 miss，全部退化为关键词匹配。
 *
 * <p>若 Qdrant 不可达，只记录 error 日志并提前返回，
 * 系统以纯关键词匹配模式运行，不中断启动。
 *
 * @author master
 */
@Slf4j
@Component
@Order(20)
@RequiredArgsConstructor
public class SchemaIndexingBootstrap implements ApplicationRunner {

    private final DataSourceMapper       dataSourceMapper;
    private final SchemaIndexingFacade   schemaIndexingFacade;
    private final SchemaIndexingService  schemaIndexingService;

    /** 与text-embedding-v3 模型维度一致 */
    private static final int VECTOR_SIZE = 1024;

    @Override
    public void run(ApplicationArguments args) {
        log.info("===== [SchemaIndexingBootstrap] 向量索引预热开始 =====");

        // 确保 Collection 存在（首次部署自动建库）
        try {
            schemaIndexingService.ensureCollection(VECTOR_SIZE);
        } catch (Exception e) {
            log.error("[SchemaIndexingBootstrap] Qdrant 不可用，" +
                      "向量检索将全程降级为关键词匹配: {}", e.getMessage());
            return;
        }

        // 查询所有未逻辑删除的数据源
        List<DataSource> allDs = dataSourceMapper.selectList(null);
        if (allDs.isEmpty()) {
            log.info("[SchemaIndexingBootstrap] 无数据源，跳过预热");
            return;
        }

        List<Long> dsIds = allDs.stream().map(DataSource::getId).toList();
        log.info("[SchemaIndexingBootstrap] 发现 {} 个数据源，开始全量重建...", dsIds.size());

        // 同步批量重建
        schemaIndexingFacade.rebuildBatch(dsIds);

        log.info("===== [SchemaIndexingBootstrap] 向量索引预热完成 =====");
    }
}