package com.shanchuang.modules.testsupport;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/** H2（MySQL 兼容模式）跑真实 SQL：预扣的条件更新与并发不超扣打桩测不出来 */
@SpringBootTest(
        classes = ModuleTestApp.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:shanchuang_module;MODE=MySQL;DB_CLOSE_DELAY=-1",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.datasource.username=sa",
                "spring.datasource.password=",
                "spring.sql.init.mode=always",
                "spring.sql.init.schema-locations=classpath:db/module-test-schema.sql"
        })
public abstract class ModuleTestBase {

    private static final String[] TABLES = {
            "sv_user", "sv_sms_code", "sv_persona", "sv_option_item",
            "sv_credit_account", "sv_credit_txn", "sv_recharge_order", "sv_script"
    };

    @Autowired
    protected JdbcTemplate jdbc;

    @BeforeEach
    void resetTables() {
        for (String t : TABLES) {
            jdbc.execute("TRUNCATE TABLE " + t);
        }
    }

    protected long count(String sql, Object... args) {
        Long n = jdbc.queryForObject("SELECT COUNT(*) FROM " + sql, Long.class, args);
        return n == null ? 0 : n;
    }

    protected int intOf(String sql, Object... args) {
        Integer n = jdbc.queryForObject(sql, Integer.class, args);
        return n == null ? 0 : n;
    }
}
