package com.wwb.govflow;

import com.wwb.govflow.dialect.DialectType;
import com.wwb.govflow.dialect.XinchuangDialectTranslator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("信创达梦 (DM8) / MySQL 双库方言转译引擎全量断言测试")
public class XinchuangDialectTest {

    private final XinchuangDialectTranslator translator = new XinchuangDialectTranslator();

    @Test
    @DisplayName("测试1：反引号标识符转换为大写双引号规范")
    void testBacktickToDoubleQuotes() {
        String sql = "SELECT `id`, `serial_no`, `user_name` FROM `gov_approval`";
        String translated = translator.translate(sql, DialectType.DAMENG_DM8);
        assertTrue(translated.contains("\"ID\""));
        assertTrue(translated.contains("\"SERIAL_NO\""));
        assertTrue(translated.contains("\"USER_NAME\""));
        assertTrue(translated.contains("\"GOV_APPROVAL\""));
        assertFalse(translated.contains("`"));
    }

    @Test
    @DisplayName("测试2：分页语法转换 LIMIT offset, count -> LIMIT count OFFSET offset")
    void testPaginationDialect() {
        String sql = "SELECT * FROM approval_table WHERE status = 'PASS' LIMIT 20, 10";
        String translated = translator.translate(sql, DialectType.DAMENG_DM8);
        assertTrue(translated.contains("LIMIT 10 OFFSET 20"));
        assertFalse(translated.contains("LIMIT 20, 10"));
    }

    @Test
    @DisplayName("测试3：空值函数 IFNULL 转换为达梦 NVL 函数")
    void testIfnullToNvl() {
        String sql = "SELECT IFNULL(remark, '默认说明'), IFNULL(auditor, '系统自动') FROM audit_log";
        String translated = translator.translate(sql, DialectType.DAMENG_DM8);
        assertTrue(translated.contains("NVL(remark, '默认说明')"));
        assertTrue(translated.contains("NVL(auditor, '系统自动')"));
        assertFalse(translated.contains("IFNULL"));
    }

    @Test
    @DisplayName("测试4：当前系统时间 NOW() 转换为 CURRENT_TIMESTAMP()")
    void testNowFunction() {
        String sql = "UPDATE audit_record SET update_time = NOW() WHERE id = 1";
        String translated = translator.translate(sql, DialectType.DAMENG_DM8);
        assertTrue(translated.contains("CURRENT_TIMESTAMP()"));
        assertFalse(translated.contains("NOW()"));
    }

    @Test
    @DisplayName("测试5：达梦保留字 USER, DOMAIN, COMMENT 自动规避")
    void testReservedWordsProtection() {
        String sql = "SELECT id, user, domain, comment FROM config_table";
        String translated = translator.translate(sql, DialectType.DAMENG_DM8);
        assertTrue(translated.contains("\"USER\""));
        assertTrue(translated.contains("\"DOMAIN\""));
        assertTrue(translated.contains("\"COMMENT\""));
    }

    @Test
    @DisplayName("测试6：目标方言为 MySQL 时原样返回不增加额外开销")
    void testMysqlNoOp() {
        String sql = "SELECT `id` FROM `user` LIMIT 0, 10";
        String translated = translator.translate(sql, DialectType.MYSQL);
        assertEquals(sql, translated);
    }
}
