# SQL-Insight 🚀

SQL-Insight 是一个利用大语言模型（LLM）能力的数据库智能助手。

### ✨ 核心功能
* **自然语言查询**：直接输入“查询去年销售额前十的城市”，AI 自动生成并执行 SQL。
* **多库支持**：动态配置管理多个数据库连接。
* **查询历史**：记录每一次 AI 生成的 SQL 及其执行结果。

### 🛠️ 技术栈
* **后端**: Spring Boot 3.5.x, MyBatis-Plus
* **AI 框架**: LangChain4j
* **数据库**: MySQL 8.0+
* **语言**: Java 17

### 🚀 快速开始
1. 克隆项目：`git clone ...`
2. 执行 `sql_insight.sql` 初始化元数据库。
3. 在 `application.yml` 中配置你的 API_KEY。
