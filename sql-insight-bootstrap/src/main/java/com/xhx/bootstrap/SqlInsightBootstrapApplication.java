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

    public static void main(String[] args) {
        SpringApplication.run(SqlInsightBootstrapApplication.class, args);
    }

}
