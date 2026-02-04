package com.xhx.web.controller;

import com.xhx.common.result.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author master
 */
@RestController
@RequestMapping("/api/health")
public class HealthController {

    @GetMapping("/check")
    public Result<String> check() {
        return Result.success("SQL-Insight 服务运行正常！");
    }
}