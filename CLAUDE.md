# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

SQL-Insight is an AI-powered database assistant that enables natural language querying. Users input questions like "query top 10 cities by sales last year", and the system uses LLM to generate and execute SQL automatically. Built with Spring Boot 3.5.x backend and Vue 3 frontend.

## Commands

### Backend (Maven multi-module)

```bash
# Build all modules
cd backend && mvn clean install

# Run the application (bootstrap is the main module)
cd backend/sql-insight-bootstrap && mvn spring-boot:run

# Run tests for a specific module
cd backend/sql-insight-core && mvn test

# Run a single test class
mvn test -Dtest=ClassNameTest

# Run a single test method
mvn test -Dtest=ClassNameTest#methodName
```

### Frontend (Vue 3 + Vite)

```bash
cd frontend

# Install dependencies
npm install

# Development server (proxies /api to localhost:8080)
npm run dev

# Build for production
npm run build

# Preview production build
npm run preview
```

### Database Setup

1. Initialize the metadata database using [database/schema.sql](database/schema.sql)
2. Configure API key in `application.yml` (OpenAI/Aliyun DashScope for LLM, Qdrant for vector storage)

## Backend Module Architecture

The backend follows a layered multi-module architecture:

```
sql-insight-bootstrap    # Application entry point, Spring Boot main class
├── sql-insight-web      # Controllers, Security config, JWT filter, SSE adapter
├── sql-insight-core     # Business logic, services, metadata extraction
├── sql-insight-ai       # LLM integration, vector indexing, schema linking
├── sql-insight-dal      # Data access layer, entities, mappers, dynamic DataSource
└── sql-insight-common   # Shared utilities, exceptions, constants
```

### Key Architectural Patterns

**Module Dependency Flow**: `web → core → ai + dal → common`. Core depends on both ai and dal. The `ai` module never depends on `core` - this separation keeps LLM logic isolated.

**Schema Linking Pipeline**: When generating SQL, relevant tables must be filtered from the full schema:
1. `SqlGeneratorServiceImpl` calls `SchemaLinker.link(question, dataSourceId, candidates)`
2. `VectorSchemaLinker` (primary) does semantic search in Qdrant vector DB
3. `KeywordSchemaLinker` (fallback) uses keyword matching when vector search fails
4. Results reduce schema noise in prompts, lowering token cost and hallucination risk

**Multi-Database Metadata Extraction**: `MetadataExtractorRouter` selects the appropriate `MetadataExtractor` implementation based on `dbType` (mysql/postgresql/sqlserver). Each extractor extracts `TableMetadata` including columns, types, and comments.

**Dynamic DataSource Management**: `DynamicDataSourceManager` maintains a cache of HikariCP connection pools keyed by `dataSourceId`. When a DataSource configuration changes, the old pool is destroyed and recreated.

**SSE Streaming Chat**: `SseChatAdapter` implements `ChatStreamListener` to push events via Server-Sent Events. The frontend `streamChat()` function parses these events (stage/sql/data/summary/done/error) in real-time.

**Role-Based Access Control**: Three roles with hierarchy: `SUPER_ADMIN > ADMIN > USER`. `SecurityConfig` defines the hierarchy; roles control visibility of menu items (frontend router) and API access (`@PreAuthorize`).

**Permission Caching**: `CacheService` caches user permissions and role IDs. `PermissionLoader` loads permissions from cache or database. `RolePermissionChangedEvent` triggers cache eviction.

## Frontend Architecture

**State Management**: Pinia stores in `src/stores/`:
- `auth.ts`: User info, token, login/logout
- `chat.ts`: Chat sessions, current session selection

**API Layer**: Axios wrappers in `src/api/`. Notable: `chat.ts` exports `streamChat()` for SSE connections.

**Route Guards**: `router/index.ts` checks authentication and role-based access. Routes declare required roles in `meta.roles`.

**Auto-Import**: Vite plugins auto-import Vue/Router/Pinia APIs and Element Plus components. No manual imports needed for `ref`, `computed`, `useRouter`, etc.

## Key Files to Understand

- [backend/sql-insight-bootstrap/src/main/resources/application.yml](backend/sql-insight-bootstrap/src/main/resources/application.yml) - Server port, JWT secret, MyBatis config
- [backend/sql-insight-core/src/main/java/com/xhx/core/service/sql/Impl/SqlGeneratorServiceImpl.java](backend/sql-insight-core/src/main/java/com/xhx/core/service/sql/Impl/SqlGeneratorServiceImpl.java) - Core SQL generation flow
- [backend/sql-insight-ai/src/main/java/com/xhx/ai/service/SchemaLinker.java](backend/sql-insight-ai/src/main/java/com/xhx/ai/service/SchemaLinker.java) - Schema linking interface
- [backend/sql-insight-dal/src/main/java/com/xhx/dal/config/DynamicDataSourceManager.java](backend/sql-insight-dal/src/main/java/com/xhx/dal/config/DynamicDataSourceManager.java) - Connection pool management
- [frontend/src/api/chat.ts](frontend/src/api/chat.ts) - SSE streaming implementation

## Additional Context

**Language Support**: Backend uses Chinese in code (comments, variable names) and error messages. Frontend is Chinese-first.

**LLM Providers**: Supports OpenAI and Aliyun DashScope via LangChain4j 0.36.2. Configured in `application.yml`.

**Vector Store**: Qdrant used for schema embedding storage during schema linking phase.