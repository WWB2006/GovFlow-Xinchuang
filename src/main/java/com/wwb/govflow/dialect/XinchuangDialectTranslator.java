package com.wwb.govflow.dialect;

import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 国产信创达梦数据库 (DM8) 与 MySQL 8.0 双库方言动态转换引擎
 * 针对大型政企信息化与信创升级项目核心痛点：
 * 1. 标识符反引号转双引号与大写规范
 * 2. 分页语法转换: LIMIT offset, count -> LIMIT count OFFSET offset
 * 3. 常用函数适配: IFNULL -> NVL, NOW() -> CURRENT_TIMESTAMP()
 * 4. 达梦保留字规避: DOMAIN, USER, COMMENT 等
 */
@Component
public class XinchuangDialectTranslator {

    private static final Pattern LIMIT_TWO_ARGS = Pattern.compile("(?i)\\bLIMIT\\s+(\\d+)\\s*,\\s*(\\d+)");
    private static final Pattern BACKTICKS = Pattern.compile("`([^`]+)`");
    private static final Pattern DATE_FORMAT = Pattern.compile("(?i)DATE_FORMAT\\s*\\(([^,]+),\\s*'([^']+)'\\)");

    // 达梦 DM8 常见冲突保留关键字集合
    private static final Set<String> DM_RESERVED_WORDS = new HashSet<>(Arrays.asList(
            "DOMAIN", "USER", "COMMENT", "ZONE", "RESOURCE", "STATUS", "LEVEL", "MODE", "TIME"
    ));

    /**
     * 将标准 MySQL SQL 语句转译为目标数据库方言
     */
    public String translate(String sql, DialectType targetDialect) {
        if (sql == null || sql.trim().isEmpty()) {
            return sql;
        }

        if (targetDialect == DialectType.MYSQL) {
            return sql;
        }

        String translated = sql;

        // 1. 标识符反引号处理: `col` -> "COL"
        Matcher backtickMatcher = BACKTICKS.matcher(translated);
        StringBuffer sb = new StringBuffer();
        while (backtickMatcher.find()) {
            String identifier = backtickMatcher.group(1);
            // 达梦规范：大写标识符加双引号
            backtickMatcher.appendReplacement(sb, "\"" + identifier.toUpperCase() + "\"");
        }
        backtickMatcher.appendTail(sb);
        translated = sb.toString();

        // 2. 分页语法转换: LIMIT offset, count -> LIMIT count OFFSET offset
        Matcher limitMatcher = LIMIT_TWO_ARGS.matcher(translated);
        if (limitMatcher.find()) {
            String offset = limitMatcher.group(1);
            String count = limitMatcher.group(2);
            translated = limitMatcher.replaceFirst("LIMIT " + count + " OFFSET " + offset);
        }

        // 3. 空值函数转换: IFNULL(a, b) -> NVL(a, b)
        translated = translated.replaceAll("(?i)\\bIFNULL\\s*\\(", "NVL(");

        // 4. 当前时间函数: NOW() -> CURRENT_TIMESTAMP()
        translated = translated.replaceAll("(?i)\\bNOW\\s*\\(\\s*\\)", "CURRENT_TIMESTAMP()");

        // 5. 日期格式化函数: DATE_FORMAT(d, '%Y-%m-%d') -> TO_CHAR(d, 'YYYY-MM-DD')
        Matcher dateMatcher = DATE_FORMAT.matcher(translated);
        if (dateMatcher.find()) {
            String col = dateMatcher.group(1).trim();
            String format = dateMatcher.group(2)
                    .replace("%Y", "YYYY")
                    .replace("%m", "MM")
                    .replace("%d", "DD")
                    .replace("%H", "HH24")
                    .replace("%i", "MI")
                    .replace("%s", "SS");
            translated = dateMatcher.replaceFirst("TO_CHAR(" + col + ", '" + format + "')");
        }

        // 6. 保留字规避检查
        for (String reserved : DM_RESERVED_WORDS) {
            // 匹配未被引号包裹的孤立保留字
            Pattern p = Pattern.compile("(?i)\\b" + reserved + "\\b");
            Matcher m = p.matcher(translated);
            translated = m.replaceAll("\"" + reserved.toUpperCase() + "\"");
        }

        return translated;
    }
}
