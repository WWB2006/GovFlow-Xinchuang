package com.wwb.govflow.controller;

import com.wwb.govflow.dialect.DialectType;
import com.wwb.govflow.dialect.XinchuangDialectTranslator;
import com.wwb.govflow.service.SlowSqlBenchmarkService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Api(tags = "2. 信创方言转换与慢SQL调优实验接口")
@RestController
@RequestMapping("/api/benchmark")
@CrossOrigin
public class BenchmarkController {

    private final XinchuangDialectTranslator translator;
    private final SlowSqlBenchmarkService benchmarkService;

    public BenchmarkController(XinchuangDialectTranslator translator, SlowSqlBenchmarkService benchmarkService) {
        this.translator = translator;
        this.benchmarkService = benchmarkService;
    }

    @ApiOperation(value = "SQL 方言转译演示 (MySQL 8 -> 达梦 DM8)")
    @PostMapping("/translate")
    public Map<String, Object> translateSql(
            @ApiParam("原始 MySQL SQL") @RequestParam(defaultValue = "SELECT `id`, `user`, IFNULL(`comment`, '无') FROM `gov_approval` WHERE `status` = 'APPROVED' ORDER BY `apply_time` DESC LIMIT 0, 20") String sql,
            @ApiParam("目标方言") @RequestParam(defaultValue = "DAMENG_DM8") DialectType targetDialect
    ) {
        long start = System.nanoTime();
        String result = translator.translate(sql, targetDialect);
        long end = System.nanoTime();

        return Map.of(
                "sourceDialect", "MySQL 8.0",
                "targetDialect", targetDialect.getDescription(),
                "originalSql", sql,
                "translatedSql", result,
                "translationLatencyUs", (end - start) / 1000.0
        );
    }

    @ApiOperation(value = "执行慢SQL联合索引调优实验 (对比中位耗时与执行计划)")
    @GetMapping("/slow-sql")
    public SlowSqlBenchmarkService.BenchmarkResult runSlowSqlBenchmark(
            @ApiParam("采样轮数") @RequestParam(defaultValue = "30") int iterations
    ) {
        return benchmarkService.runBenchmark(iterations);
    }
}
