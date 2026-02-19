package com.xhx.bootstrap;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * @author master
 */
@SpringBootApplication(scanBasePackages = "com.xhx")
@MapperScan("com.xhx.dal.mapper")
public class SqlInsightBootstrapApplication {
    // TODO 缓存机制要大改，需要做到登录时缓存一次，缓存有变动时只删除缓存，用户查询时先查缓存，没查到在查db
    //  还需要实现根据数据源查询表的缓存，该操作耗时长

    public static void main(String[] args) {
        SpringApplication.run(SqlInsightBootstrapApplication.class, args);
    }

}
