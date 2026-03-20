# SQL-Insight

基于大语言模型的数据库智能助手，支持自然语言查询、多数据源管理、细粒度权限控制。

## 核心功能

- **自然语言转 SQL**：输入"查询去年销售额前十的城市"，AI 自动生成并执行 SQL
- **多数据源支持**：动态管理多个数据库连接，支持 MySQL、PostgreSQL、SQL Server
- **Schema 智能检索**：基于向量数据库的表结构检索，降低 Token 消耗，减少幻觉
- **流式响应**：SSE 实时推送 SQL 生成进度、执行结果和 AI 总结
- **智能图表推荐**：根据查询结果自动推荐可视化方案
- **权限控制**：角色层级 + 表级权限 + 查询策略（JOIN/子查询/LIMIT/聚合）

## 技术栈

| 层级 | 技术 |
|------|------|
| 后端框架 | Spring Boot 3.5.10 + Java 17 |
| AI 框架 | LangChain4j 0.36.2 |
| 向量数据库 | Qdrant |
| 关系数据库 | MySQL 8.0+ |
| 缓存 | Redis |
| 前端框架 | Vue 3 + Vite + Element Plus |
| 状态管理 | Pinia |

## 系统架构

```
┌─────────────────────────────────────────────────────────────┐
│                        Frontend (Vue 3)                      │
│  ┌─────────┐  ┌─────────┐  ┌─────────┐  ┌─────────────────┐ │
│  │  Chat   │  │DataSource│  │  User   │  │   Permission    │ │
│  └────┬────┘  └────┬────┘  └────┬────┘  └────────┬────────┘ │
└───────┼────────────┼────────────┼─────────────────┼─────────┘
        │            │            │                 │
        ▼            ▼            ▼                 ▼
┌─────────────────────────────────────────────────────────────┐
│                    Backend (Spring Boot)                     │
│  ┌─────────────────────────────────────────────────────┐    │
│  │                    sql-insight-web                   │    │
│  │         Controllers / Security / SSE Adapter         │    │
│  └─────────────────────────┬───────────────────────────┘    │
│                            ▼                                 │
│  ┌─────────────────────────────────────────────────────┐    │
│  │                   sql-insight-core                   │    │
│  │    SQL Pipeline / Metadata / Permission / Cache      │    │
│  └──────────┬─────────────────────────────┬─────────────┘    │
│             ▼                               ▼                 │
│  ┌──────────────────────┐    ┌──────────────────────┐        │
│  │   sql-insight-ai     │    │   sql-insight-dal    │        │
│  │ LLM / Vector / Embed │    │ Entities / Mappers   │        │
│  └──────────────────────┘    └──────────────────────┘        │
└─────────────────────────────────────────────────────────────┘
        │                 │                    │
        ▼                 ▼                    ▼
┌──────────────┐  ┌──────────────┐  ┌──────────────────────┐
│  DeepSeek    │  │    Qdrant    │  │  MySQL / PostgreSQL  │
│   OpenAI     │  │  (Vector DB) │  │     SQL Server       │
└──────────────┘  └──────────────┘  └──────────────────────┘
```

## 快速开始

### 环境要求

- JDK 17+
- Node.js 18+
- MySQL 8.0+
- Redis 6.0+
- Qdrant 1.11+

### 1. 克隆项目

```bash
git clone https://github.com/your-username/sql-insight.git
cd sql-insight
```

### 2. 初始化数据库

```bash
mysql -u root -p < database/schema.sql
```

### 3. 配置后端

```bash
# 复制配置模板
cp backend/sql-insight-bootstrap/src/main/resources/application-local.example.yml \
   backend/sql-insight-bootstrap/src/main/resources/application-local.yml

# 编辑配置文件，填入你的 API Key 和数据库连接信息
```

配置项说明：

| 配置项 | 说明 |
|--------|------|
| `langchain4j.open-ai.chat-model.api-key` | LLM API Key（支持 DeepSeek、OpenAI 等） |
| `aliyun.dashscope.api-key` | 阿里云 DashScope API Key（用于 Embedding） |
| `qdrant.host` | Qdrant 向量数据库地址 |
| `ds.encrypt-key` | 数据源密码加密密钥（AES-256，32字节 Base64） |

### 4. 启动后端

```bash
cd backend
mvn clean install
cd sql-insight-bootstrap
mvn spring-boot:run
```

后端启动后访问：http://localhost:8080

### 5. 启动前端

```bash
cd frontend
npm install
npm run dev
```

前端启动后访问：http://localhost:5173

### 默认账号

系统内置默认密码 `default123`，首次登录后请及时修改。

## 项目结构

```
sql-insight/
├── backend/                          # 后端 Maven 多模块项目
│   ├── sql-insight-bootstrap/        # 启动模块
│   ├── sql-insight-web/              # Web 层：Controller、Security
│   ├── sql-insight-core/             # 核心业务：SQL 生成管道、权限
│   ├── sql-insight-ai/               # AI 能力：LLM、向量检索
│   ├── sql-insight-dal/              # 数据访问：实体、Mapper
│   └── sql-insight-common/           # 公共模块：工具类、异常
├── frontend/                         # 前端 Vue 3 项目
│   ├── src/
│   │   ├── api/                      # API 接口
│   │   ├── stores/                   # Pinia 状态管理
│   │   ├── views/                    # 页面组件
│   │   └── router/                   # 路由配置
│   └── vite.config.ts
└── database/                         # 数据库脚本
    └── schema.sql
```

## 核心设计

### SQL 生成管道

SQL 生成采用 Pipeline 模式，包含 9 个阶段：

```
Session → DataSource → Role → Permission → Metadata → SchemaLink → Prompt → LLM → Validation
```

每个阶段职责单一，支持短路退出，便于扩展和测试。

### Schema 智能检索

当数据库表数量较多时，将所有表结构放入 Prompt 会消耗大量 Token 且容易产生幻觉。本系统采用向量检索方案：

1. 预先将表结构 Embedding 存入 Qdrant
2. 用户提问时进行语义检索，找出最相关的表
3. 仅将相关表的 Schema 加入 Prompt

### 权限控制

- **角色层级**：SUPER_ADMIN > ADMIN > USER
- **表级权限**：控制用户可访问的表
- **查询策略**：限制 JOIN、子查询、LIMIT、聚合操作

## 许可证

MIT License