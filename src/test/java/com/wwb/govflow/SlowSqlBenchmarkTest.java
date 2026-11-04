package com.wwb.govflow;

import com.wwb.govflow.service.SlowSqlBenchmarkService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@DisplayName("慢 SQL 联合索引优化与 EXPLAIN 执行计划测试")
public class SlowSqlBenchmarkTest {

    @Autowired
    private SlowSqlBenchmarkService benchmarkService;

    @Test
    @DisplayName("基准测试：全表扫描 vs 联合索引对比采样，验证耗时降低与索引命中")
    void testSlowSqlBenchmark() {
        SlowSqlBenchmarkService.BenchmarkResult res = benchmarkService.runBenchmark(10);
        assertNotNull(res);
        assertTrue(res.iterations >= 10);
        assertTrue(res.medianIndexedMs >= 0);
        assertTrue(res.medianUnindexedMs >= 0);
        assertNotNull(res.explainPlan);
        assertTrue(res.explainPlan.contains("IDX_ITEM_STATUS_TIME") || res.explainPlan.contains("idx_item_status_time"));
    }
}
