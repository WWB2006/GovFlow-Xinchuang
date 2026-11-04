package com.wwb.govflow.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.*;

@Service
public class SlowSqlBenchmarkService {

    private static final Logger log = LoggerFactory.getLogger(SlowSqlBenchmarkService.class);

    private final JdbcTemplate jdbcTemplate;

    public SlowSqlBenchmarkService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostConstruct
    public void initDatabaseSchema() {
        try {
            // 初始化合成审批历史表
            jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS gov_approval_history (" +
                    "id BIGINT AUTO_INCREMENT PRIMARY KEY," +
                    "serial_no VARCHAR(64) NOT NULL," +
                    "item_code VARCHAR(32) NOT NULL," +
                    "applicant_name VARCHAR(64) NOT NULL," +
                    "status VARCHAR(32) NOT NULL," +
                    "apply_time TIMESTAMP NOT NULL" +
                    ")");

            // 预加载测试样本 (10,000 ~ 100,000 条合成数据结构)
            Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM gov_approval_history", Integer.class);
            if (count == null || count == 0) {
                log.info("[Benchmark] 正在快速生成合成审批历史数据集 (10,000 批次结构)...");
                List<Object[]> batch = new ArrayList<>();
                String[] items = {"ITEM-BIZ-01", "ITEM-BIZ-02", "ITEM-CONSTRUCT", "ITEM-CIVIL"};
                String[] statuses = {"SUBMITTED", "PRE_CHECKING", "DEPT_REVIEW", "APPROVED", "REJECTED"};

                for (int i = 1; i <= 10000; i++) {
                    String sNo = "GOV-SYN-" + (1000000 + i);
                    String item = items[i % items.length];
                    String st = statuses[i % statuses.length];
                    batch.add(new Object[]{sNo, item, "申请人_" + i, st, new java.sql.Timestamp(System.currentTimeMillis() - i * 1000L)});

                    if (batch.size() >= 2000) {
                        jdbcTemplate.batchUpdate("INSERT INTO gov_approval_history (serial_no, item_code, applicant_name, status, apply_time) VALUES (?, ?, ?, ?, ?)", batch);
                        batch.clear();
                    }
                }
                if (!batch.isEmpty()) {
                    jdbcTemplate.batchUpdate("INSERT INTO gov_approval_history (serial_no, item_code, applicant_name, status, apply_time) VALUES (?, ?, ?, ?, ?)", batch);
                }
                log.info("[Benchmark] 审批历史合成数据集预备完毕！");
            }
        } catch (Exception e) {
            log.warn("[Benchmark] 初始化数据库失败或表已存在: {}", e.getMessage());
        }
    }

    /**
     * 执行联合索引调优对照基准实验
     */
    public BenchmarkResult runBenchmark(int iterations) {
        if (iterations <= 0) iterations = 30;

        String querySql = "SELECT id, serial_no, item_code, status, apply_time FROM gov_approval_history WHERE item_code = 'ITEM-BIZ-01' AND status = 'APPROVED' ORDER BY apply_time DESC LIMIT 20";

        // 1. 无联合索引时的查询基准
        try {
            jdbcTemplate.execute("DROP INDEX IF EXISTS idx_item_status_time");
        } catch (Exception ignored) {}

        List<Double> unindexedTimes = new ArrayList<>();
        for (int i = 0; i < iterations; i++) {
            long start = System.nanoTime();
            jdbcTemplate.queryForList(querySql);
            long end = System.nanoTime();
            unindexedTimes.add((end - start) / 1_000_000.0);
        }

        // 2. 建立联合索引后的查询基准: (item_code, status, apply_time DESC)
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_item_status_time ON gov_approval_history(item_code, status, apply_time)");

        List<Double> indexedTimes = new ArrayList<>();
        for (int i = 0; i < iterations; i++) {
            long start = System.nanoTime();
            jdbcTemplate.queryForList(querySql);
            long end = System.nanoTime();
            indexedTimes.add((end - start) / 1_000_000.0);
        }

        // 计算中位耗时
        Collections.sort(unindexedTimes);
        Collections.sort(indexedTimes);
        double medianUnindexed = unindexedTimes.get(unindexedTimes.size() / 2);
        double medianIndexed = indexedTimes.get(indexedTimes.size() / 2);

        // 获取 EXPLAIN 执行计划
        List<Map<String, Object>> explainPlan = jdbcTemplate.queryForList("EXPLAIN " + querySql);

        return new BenchmarkResult(
                iterations,
                medianUnindexed,
                medianIndexed,
                (medianUnindexed - medianIndexed) / medianUnindexed * 100.0,
                explainPlan.toString()
        );
    }

    public static class BenchmarkResult {
        public int iterations;
        public double medianUnindexedMs;
        public double medianIndexedMs;
        public double improvementPercent;
        public String explainPlan;

        public BenchmarkResult(int iterations, double medianUnindexedMs, double medianIndexedMs, double improvementPercent, String explainPlan) {
            this.iterations = iterations;
            this.medianUnindexedMs = medianUnindexedMs;
            this.medianIndexedMs = medianIndexedMs;
            this.improvementPercent = improvementPercent;
            this.explainPlan = explainPlan;
        }
    }
}
