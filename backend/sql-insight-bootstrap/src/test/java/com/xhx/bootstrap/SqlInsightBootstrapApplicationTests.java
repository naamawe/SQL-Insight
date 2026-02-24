package com.xhx.bootstrap;

import com.xhx.common.util.CommonUtil;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class SqlInsightBootstrapApplicationTests {

    @Test
    void contextLoads() {
        // 这里是模拟 aiReply.text() 真正拿到的字符串内容
        // 已经去掉了 AiMessage { text = ... } 等外壳
        String raw = """
                SELECT * FROM user LIMIT 100;

                让我详细解释一下这个查询：

                **查询说明：**
                1. `SELECT *` - 选择所有列
                2. `FROM user` - 从user表查询
                3. `LIMIT 100` - 限制返回100条记录（这是为了保护数据库性能，避免返回过多数据）

                **如果您需要更多信息，可以尝试：**

                1. **查看表结构：**
                ```sql
                DESCRIBE user;
                -- 或
                SHOW COLUMNS FROM user;
                ```

                2. **查看记录总数：**
                ```sql
                SELECT COUNT(*) FROM user;
                ```

                3. **查看特定列的数据：**
                ```sql
                SELECT id, username, email FROM user LIMIT 100;
                ```

                4. **添加排序：**
                ```sql
                SELECT * FROM user ORDER BY id DESC LIMIT 100;
                ```

                5. **查看更多记录（谨慎使用）：**
                ```sql
                SELECT * FROM user LIMIT 1000;
                ```

                **注意事项：**
                - 如果表很大，直接查询所有数据可能导致性能问题
                - 建议先了解表的大小和结构
                - 在生产环境中，最好使用分页查询

                您具体想了解user表的哪些信息呢？比如用户数量、特定字段的内容，还是其他什么？""";

        // 调用你更新后的 cleanSql
        String s = CommonUtil.cleanSql(raw);

        System.out.println("--- 清洗后的结果 ---");
        System.out.println(s);

        // 断言验证（预期应该是拿到第一行的 SELECT 语句）
        assert s.startsWith("SELECT * FROM user LIMIT 100;");
    }

}
