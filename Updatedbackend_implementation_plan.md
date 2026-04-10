# Numera Backend — Implementation Specification

> **This document is an AI-implementable specification.** Every class, method, SQL statement, and configuration value is specified precisely enough for autonomous implementation.

---

## 0. Architecture: Modular Monolith → Microservice Migration Path

### Why Modular Monolith (Not Microservices)

| Concern | Microservices | Modular Monolith |
|---|---|---|
| Solo developer productivity | ❌ 10+ repos, Docker networking, service discovery | ✅ 1 repo, 1 deploy, simple debugging |
| Distributed transactions | ❌ Saga pattern needed for spread+audit | ✅ Single `@Transactional` |
| Data consistency | ❌ Eventual consistency everywhere | ✅ Strong consistency where needed |
| Network latency | ❌ Service-to-service HTTP adds 5-20ms per hop | ✅ In-process method calls |
| Debugging | ❌ Distributed tracing required from day 1 | ✅ Single stack trace |
| Deployment | ❌ K8s + Helm + service mesh from day 1 | ✅ Single JAR/container |

### Migration Path (When You Need It)

Spring Modulith enforces that modules communicate **only** through:
1. **Published domain events** (not direct method calls between modules)
2. **Explicitly exported service interfaces** (declared in `package-info.java`)

When you extract a module to a microservice:

```
BEFORE (Monolith):
  spreading module → publishes SpreadSubmittedEvent → covenant module listens (in-memory)

AFTER (Microservices):
  spreading-service → publishes SpreadSubmittedEvent → Kafka topic → covenant-service listens
  
  Changes needed:
  1. Add @Externalized("spread-events") annotation to SpreadSubmittedEvent
  2. Add spring-modulith-events-kafka dependency to covenant-service
  3. Move covenant/ package to new Spring Boot project
  4. That's it. Zero business logic changes.
```

### Module Dependency Rules (Enforced at Compile Time)

```
auth ←── (no dependencies)
customer ←── auth
document ←── auth, storage (port)
model ←── auth
spreading ←── auth, customer, document, model, ml-client (port)
covenant ←── auth, customer, spreading (via events only)
reporting ←── auth (reads from all modules via read-only views)
admin ←── auth, model
integration ←── spreading, covenant (adapter interfaces)
```

**RULE**: No module may import classes from another module's `internal` package. Only `api` and `events` packages are visible. Spring Modulith verifies this.

---

## 1. Project Bootstrap

### 1.1 Directory Structure

Create the following directory structure under `f:\Context\backend\`:

```
backend/
├── build.gradle.kts
├── settings.gradle.kts
├── gradle.properties
├── docker-compose.yml                    # Local dev infrastructure
├── src/
│   ├── main/
│   │   ├── kotlin/com/numera/
│   │   │   ├── NumeraApplication.kt
│   │   │   ├── shared/                   # ── SHARED KERNEL ──
│   │   │   │   ├── config/
│   │   │   │   │   ├── SecurityConfig.kt
│   │   │   │   │   ├── WebConfig.kt
│   │   │   │   │   ├── JacksonConfig.kt
│   │   │   │   │   └── AsyncConfig.kt
│   │   │   │   ├── security/
│   │   │   │   │   ├── JwtTokenProvider.kt
│   │   │   │   │   ├── JwtAuthenticationFilter.kt
│   │   │   │   │   ├── CurrentUserProvider.kt
│   │   │   │   │   └── TenantContext.kt
│   │   │   │   ├── audit/
│   │   │   │   │   ├── AuditEvent.kt
│   │   │   │   │   ├── AuditAction.kt
│   │   │   │   │   ├── AuditService.kt
│   │   │   │   │   ├── HashChainService.kt
│   │   │   │   │   └── EventLogRepository.kt
│   │   │   │   ├── domain/
│   │   │   │   │   ├── BaseEntity.kt
│   │   │   │   │   └── TenantAwareEntity.kt
│   │   │   │   ├── exception/
│   │   │   │   │   ├── ApiException.kt
│   │   │   │   │   ├── ErrorCode.kt
│   │   │   │   │   ├── ErrorResponse.kt
│   │   │   │   │   └── GlobalExceptionHandler.kt
│   │   │   │   └── util/
│   │   │   │       └── Extensions.kt
│   │   │   │
│   │   │   ├── auth/                     # ── AUTH MODULE ──
│   │   │   │   ├── api/
│   │   │   │   │   └── AuthController.kt
│   │   │   │   ├── application/
│   │   │   │   │   └── AuthService.kt
│   │   │   │   ├── domain/
│   │   │   │   │   ├── User.kt
│   │   │   │   │   ├── Role.kt
│   │   │   │   │   ├── Permission.kt
│   │   │   │   │   └── RefreshToken.kt
│   │   │   │   ├── dto/
│   │   │   │   │   ├── LoginRequest.kt
│   │   │   │   │   ├── LoginResponse.kt
│   │   │   │   │   ├── RefreshRequest.kt
│   │   │   │   │   └── UserProfile.kt
│   │   │   │   └── infrastructure/
│   │   │   │       ├── UserRepository.kt
│   │   │   │       ├── RoleRepository.kt
│   │   │   │       └── RefreshTokenRepository.kt
│   │   │   │
│   │   │   ├── customer/                 # ── CUSTOMER MODULE ──
│   │   │   │   ├── api/
│   │   │   │   │   └── CustomerController.kt
│   │   │   │   ├── application/
│   │   │   │   │   └── CustomerService.kt
│   │   │   │   ├── domain/
│   │   │   │   │   └── Customer.kt
│   │   │   │   ├── dto/
│   │   │   │   │   ├── CustomerRequest.kt
│   │   │   │   │   ├── CustomerResponse.kt
│   │   │   │   │   └── CustomerSearchRequest.kt
│   │   │   │   └── infrastructure/
│   │   │   │       └── CustomerRepository.kt
│   │   │   │
│   │   │   ├── document/                 # ── DOCUMENT MODULE ──
│   │   │   │   ├── api/
│   │   │   │   │   └── DocumentController.kt
│   │   │   │   ├── application/
│   │   │   │   │   └── DocumentProcessingService.kt
│   │   │   │   ├── domain/
│   │   │   │   │   ├── Document.kt
│   │   │   │   │   ├── DetectedZone.kt
│   │   │   │   │   └── DocumentStatus.kt
│   │   │   │   ├── dto/
│   │   │   │   │   ├── DocumentResponse.kt
│   │   │   │   │   ├── DocumentStatusResponse.kt
│   │   │   │   │   ├── ZoneResponse.kt
│   │   │   │   │   └── ZoneUpdateRequest.kt
│   │   │   │   ├── events/
│   │   │   │   │   └── DocumentProcessedEvent.kt
│   │   │   │   └── infrastructure/
│   │   │   │       ├── DocumentRepository.kt
│   │   │   │       ├── DetectedZoneRepository.kt
│   │   │   │       ├── MinioStorageClient.kt
│   │   │   │       └── MlServiceClient.kt
│   │   │   │
│   │   │   ├── model/                    # ── MODEL TEMPLATE MODULE ──
│   │   │   │   ├── api/
│   │   │   │   │   └── TemplateController.kt
│   │   │   │   ├── application/
│   │   │   │   │   ├── TemplateService.kt
│   │   │   │   │   └── FormulaEngine.kt
│   │   │   │   ├── domain/
│   │   │   │   │   ├── ModelTemplate.kt
│   │   │   │   │   ├── ModelLineItem.kt
│   │   │   │   │   └── ModelValidation.kt
│   │   │   │   ├── dto/
│   │   │   │   │   ├── TemplateResponse.kt
│   │   │   │   │   ├── LineItemResponse.kt
│   │   │   │   │   └── ValidationResultResponse.kt
│   │   │   │   └── infrastructure/
│   │   │   │       ├── TemplateRepository.kt
│   │   │   │       ├── LineItemRepository.kt
│   │   │   │       └── ValidationRepository.kt
│   │   │   │
│   │   │   └── spreading/               # ── SPREADING MODULE ──
│   │   │       ├── api/
│   │   │       │   ├── SpreadController.kt
│   │   │       │   └── SpreadValueController.kt
│   │   │       ├── application/
│   │   │       │   ├── SpreadService.kt
│   │   │       │   ├── MappingOrchestrator.kt
│   │   │       │   ├── AutofillService.kt
│   │   │       │   └── SpreadVersionService.kt
│   │   │       ├── domain/
│   │   │       │   ├── SpreadItem.kt
│   │   │       │   ├── SpreadValue.kt
│   │   │       │   ├── SpreadVersion.kt
│   │   │       │   ├── ExpressionPattern.kt
│   │   │       │   └── SpreadStatus.kt
│   │   │       ├── dto/
│   │   │       │   ├── SpreadItemRequest.kt
│   │   │       │   ├── SpreadItemResponse.kt
│   │   │       │   ├── SpreadValueResponse.kt
│   │   │       │   ├── SpreadValueUpdateRequest.kt
│   │   │       │   ├── MappingResultResponse.kt
│   │   │       │   ├── VersionHistoryResponse.kt
│   │   │       │   └── DiffResponse.kt
│   │   │       ├── events/
│   │   │       │   ├── SpreadSubmittedEvent.kt
│   │   │       │   └── SpreadApprovedEvent.kt
│   │   │       └── infrastructure/
│   │   │           ├── SpreadItemRepository.kt
│   │   │           ├── SpreadValueRepository.kt
│   │   │           ├── SpreadVersionRepository.kt
│   │   │           └── ExpressionPatternRepository.kt
│   │   │
│   │   └── resources/
│   │       ├── application.yml
│   │       ├── application-dev.yml
│   │       └── db/migration/
│   │           ├── V001__tenants.sql
│   │           ├── V002__auth.sql
│   │           ├── V003__customers.sql
│   │           ├── V004__documents.sql
│   │           ├── V005__model_templates.sql
│   │           ├── V006__spreading.sql
│   │           ├── V007__audit_event_log.sql
│   │           └── V008__seed_data.sql
│   │
│   └── test/kotlin/com/numera/
│       ├── ModuleBoundaryTest.kt
│       ├── auth/AuthServiceTest.kt
│       ├── document/DocumentProcessingTest.kt
│       ├── model/FormulaEngineTest.kt
│       └── spreading/MappingOrchestratorTest.kt
```

### 1.2 build.gradle.kts (Complete)

```kotlin
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("org.springframework.boot") version "3.4.4"
    id("io.spring.dependency-management") version "1.1.7"
    kotlin("jvm") version "2.1.10"
    kotlin("plugin.spring") version "2.1.10"
    kotlin("plugin.jpa") version "2.1.10"
}

group = "com.numera"
version = "0.1.0"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

extra["springModulithVersion"] = "1.3.2"

dependencies {
    // ── Spring Boot Core ──
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    implementation("org.springframework.boot:spring-boot-starter-websocket")

    // ── Spring Modulith ──
    implementation("org.springframework.modulith:spring-modulith-starter-core")
    implementation("org.springframework.modulith:spring-modulith-starter-jpa")

    // ── Kotlin ──
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")

    // ── Database ──
    runtimeOnly("org.postgresql:postgresql")
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-database-postgresql")

    // ── Storage ──
    implementation("io.minio:minio:8.5.14")

    // ── JWT ──
    implementation("io.jsonwebtoken:jjwt-api:0.12.6")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:0.12.6")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.12.6")

    // ── HTTP Client (for ML services) ──
    implementation("org.springframework.boot:spring-boot-starter-webflux")

    // ── OpenAPI ──
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.8.4")

    // ── Observability ──
    implementation("io.micrometer:micrometer-tracing-bridge-otel")
    implementation("io.opentelemetry:opentelemetry-exporter-otlp")
    runtimeOnly("io.micrometer:micrometer-registry-prometheus")

    // ── Testing ──
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.modulith:spring-modulith-starter-test")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("io.mockk:mockk:1.13.14")
    testImplementation("org.testcontainers:junit-jupiter:1.20.4")
    testImplementation("org.testcontainers:postgresql:1.20.4")
    testRuntimeOnly("com.h2database:h2")
}

dependencyManagement {
    imports {
        mavenBom("org.springframework.modulith:spring-modulith-bom:${property("springModulithVersion")}")
    }
}

tasks.withType<KotlinCompile> {
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict")
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}
```

### 1.3 docker-compose.yml (Local Development)

```yaml
# docker-compose.yml — Run: docker compose up -d
version: "3.9"

services:
  postgres:
    image: postgres:17-alpine
    container_name: numera-postgres
    environment:
      POSTGRES_DB: numera
      POSTGRES_USER: numera
      POSTGRES_PASSWORD: numera_dev
    ports:
      - "5432:5432"
    volumes:
      - pgdata:/var/lib/postgresql/data

  redis:
    image: valkey/valkey:8-alpine
    container_name: numera-redis
    ports:
      - "6379:6379"

  minio:
    image: minio/minio:latest
    container_name: numera-minio
    command: server /data --console-address ":9001"
    environment:
      MINIO_ROOT_USER: minioadmin
      MINIO_ROOT_PASSWORD: minioadmin
    ports:
      - "9000:9000"
      - "9001:9001"
    volumes:
      - miniodata:/data

  # ML services (from existing project)
  ocr-service:
    build: ../ocr-service
    container_name: numera-ocr
    ports:
      - "8001:8001"
    volumes:
      - ../data:/app/data:ro

  ml-service:
    build: ../ml-service
    container_name: numera-ml
    ports:
      - "8002:8002"
    volumes:
      - ../data:/app/data:ro

volumes:
  pgdata:
  miniodata:
```

### 1.4 application.yml (Complete)

```yaml
server:
  port: 8080
  shutdown: graceful

spring:
  application:
    name: numera-backend
  threads:
    virtual:
      enabled: true
  datasource:
    url: jdbc:postgresql://${DB_HOST:localhost}:${DB_PORT:5432}/${DB_NAME:numera}
    username: ${DB_USER:numera}
    password: ${DB_PASSWORD:numera_dev}
    hikari:
      maximum-pool-size: 20
      minimum-idle: 5
  jpa:
    hibernate:
      ddl-auto: validate
    open-in-view: false
    properties:
      hibernate:
        default_schema: public
        format_sql: false
        jdbc.batch_size: 25
        order_inserts: true
        order_updates: true
  flyway:
    enabled: true
    locations: classpath:db/migration
    baseline-on-migrate: false
  data:
    redis:
      host: ${REDIS_HOST:localhost}
      port: ${REDIS_PORT:6379}
  servlet:
    multipart:
      max-file-size: 50MB
      max-request-size: 55MB
  jackson:
    default-property-inclusion: non_null
    serialization:
      write-dates-as-timestamps: false

# ── Numera Configuration ──
numera:
  jwt:
    secret: ${JWT_SECRET:dev-secret-key-change-in-production-must-be-256-bits-long!!}
    access-token-expiry-minutes: 15
    refresh-token-expiry-days: 7
    issuer: numera
  ml:
    ocr-service-url: ${OCR_SERVICE_URL:http://localhost:8001/api}
    ml-service-url: ${ML_SERVICE_URL:http://localhost:8002/api}
    connect-timeout-ms: 5000
    read-timeout-ms: 120000
  storage:
    endpoint: ${MINIO_ENDPOINT:http://localhost:9000}
    access-key: ${MINIO_ACCESS_KEY:minioadmin}
    secret-key: ${MINIO_SECRET_KEY:minioadmin}
    bucket: ${MINIO_BUCKET:numera-documents}
    region: ${MINIO_REGION:us-east-1}
  audit:
    hash-chain-enabled: true
  processing:
    max-concurrent: 5
    async-pool-size: 10

# ── Actuator ──
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
  endpoint:
    health:
      show-details: when_authorized
```

### 1.5 NumeraApplication.kt

```kotlin
package com.numera

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableAsync

@SpringBootApplication
@ConfigurationPropertiesScan
@EnableAsync
class NumeraApplication

fun main(args: Array<String>) {
    runApplication<NumeraApplication>(*args)
}
```

---

## 2. Database Migrations (Complete SQL)

### V001__tenants.sql

```sql
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pg_trgm";
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

CREATE TABLE tenants (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    name            TEXT NOT NULL UNIQUE,
    display_name    TEXT NOT NULL,
    status          TEXT NOT NULL DEFAULT 'ACTIVE'
                    CHECK (status IN ('ACTIVE','SUSPENDED','ARCHIVED')),
    settings        JSONB NOT NULL DEFAULT '{}',
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Default tenant for demo
INSERT INTO tenants (id, name, display_name) VALUES
    ('00000000-0000-0000-0000-000000000001', 'demo', 'Demo Bank');
```

### V002__auth.sql

```sql
CREATE TABLE users (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id       UUID NOT NULL REFERENCES tenants(id),
    email           TEXT NOT NULL,
    password_hash   TEXT,
    full_name       TEXT NOT NULL,
    status          TEXT NOT NULL DEFAULT 'ACTIVE'
                    CHECK (status IN ('PENDING','ACTIVE','INACTIVE','LOCKED')),
    auth_provider   TEXT NOT NULL DEFAULT 'FORM'
                    CHECK (auth_provider IN ('FORM','SAML','OIDC')),
    last_login_at   TIMESTAMPTZ,
    login_count     INT NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (tenant_id, email)
);

CREATE TABLE roles (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id       UUID REFERENCES tenants(id),
    name            TEXT NOT NULL,
    description     TEXT,
    is_system_role  BOOLEAN NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE permissions (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    module          TEXT NOT NULL,
    action          TEXT NOT NULL,
    resource        TEXT NOT NULL,
    UNIQUE (module, action, resource)
);

CREATE TABLE role_permissions (
    role_id         UUID NOT NULL REFERENCES roles(id) ON DELETE CASCADE,
    permission_id   UUID NOT NULL REFERENCES permissions(id) ON DELETE CASCADE,
    PRIMARY KEY (role_id, permission_id)
);

CREATE TABLE user_roles (
    user_id         UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role_id         UUID NOT NULL REFERENCES roles(id) ON DELETE CASCADE,
    PRIMARY KEY (user_id, role_id)
);

CREATE TABLE refresh_tokens (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id         UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash      TEXT NOT NULL UNIQUE,
    device_info     TEXT,
    ip_address      TEXT,
    expires_at      TIMESTAMPTZ NOT NULL,
    revoked         BOOLEAN NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_users_tenant_email ON users(tenant_id, email);
CREATE INDEX idx_users_status ON users(tenant_id, status);
CREATE INDEX idx_refresh_tokens_user ON refresh_tokens(user_id);
CREATE INDEX idx_refresh_tokens_hash ON refresh_tokens(token_hash);

-- Seed roles
INSERT INTO roles (id, name, description, is_system_role) VALUES
    ('10000000-0000-0000-0000-000000000001', 'ADMIN', 'System Administrator', TRUE),
    ('10000000-0000-0000-0000-000000000002', 'ANALYST', 'Financial Analyst (Maker)', TRUE),
    ('10000000-0000-0000-0000-000000000003', 'MANAGER', 'Manager (Checker)', TRUE),
    ('10000000-0000-0000-0000-000000000004', 'AUDITOR', 'Read-Only Auditor', TRUE);

-- Seed permissions
INSERT INTO permissions (id, module, action, resource) VALUES
    (uuid_generate_v4(), 'SPREADING', 'CREATE', 'spread_item'),
    (uuid_generate_v4(), 'SPREADING', 'READ', 'spread_item'),
    (uuid_generate_v4(), 'SPREADING', 'UPDATE', 'spread_item'),
    (uuid_generate_v4(), 'SPREADING', 'SUBMIT', 'spread_item'),
    (uuid_generate_v4(), 'SPREADING', 'APPROVE', 'spread_item'),
    (uuid_generate_v4(), 'FILE_STORE', 'CREATE', 'document'),
    (uuid_generate_v4(), 'FILE_STORE', 'READ', 'document'),
    (uuid_generate_v4(), 'FILE_STORE', 'DELETE', 'document'),
    (uuid_generate_v4(), 'ADMIN', 'CREATE', 'user'),
    (uuid_generate_v4(), 'ADMIN', 'READ', 'user'),
    (uuid_generate_v4(), 'ADMIN', 'UPDATE', 'user'),
    (uuid_generate_v4(), 'ADMIN', 'DELETE', 'user');
```

### V003__customers.sql

```sql
CREATE TABLE customers (
    id                  UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id           UUID NOT NULL REFERENCES tenants(id),
    entity_id           TEXT,
    long_name           TEXT NOT NULL,
    short_name          TEXT,
    financial_year_end  TEXT DEFAULT '31 December',
    source_currency     TEXT NOT NULL DEFAULT 'USD',
    target_currency     TEXT NOT NULL DEFAULT 'USD',
    status              TEXT NOT NULL DEFAULT 'ACTIVE'
                        CHECK (status IN ('ACTIVE','INACTIVE')),
    metadata            JSONB NOT NULL DEFAULT '{}',
    created_by          UUID REFERENCES users(id),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_customers_tenant ON customers(tenant_id);
CREATE INDEX idx_customers_name ON customers USING gin(long_name gin_trgm_ops);
CREATE INDEX idx_customers_entity ON customers(tenant_id, entity_id);
```

### V004__documents.sql

```sql
CREATE TABLE documents (
    id                  UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id           UUID NOT NULL REFERENCES tenants(id),
    filename            TEXT NOT NULL,
    original_filename   TEXT NOT NULL,
    file_type           TEXT NOT NULL
                        CHECK (file_type IN ('PDF','DOCX','XLSX','JPG','PNG','TIFF')),
    file_size           BIGINT NOT NULL,
    language            TEXT NOT NULL DEFAULT 'en',
    storage_path        TEXT NOT NULL,
    processing_status   TEXT NOT NULL DEFAULT 'UPLOADED'
                        CHECK (processing_status IN (
                            'UPLOADED','PROCESSING','OCR_COMPLETE',
                            'TABLES_DETECTED','ZONES_CLASSIFIED','READY','ERROR'
                        )),
    pdf_type            TEXT CHECK (pdf_type IN ('NATIVE','SCANNED','MIXED')),
    backend_used        TEXT CHECK (backend_used IN ('native','qwen3vl','paddleocr')),
    total_pages         INT,
    processing_time_ms  INT,
    error_message       TEXT,
    uploaded_by         UUID NOT NULL REFERENCES users(id),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE detected_zones (
    id                      UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    document_id             UUID NOT NULL REFERENCES documents(id) ON DELETE CASCADE,
    page_number             INT NOT NULL,
    zone_type               TEXT NOT NULL
                            CHECK (zone_type IN (
                                'INCOME_STATEMENT','BALANCE_SHEET','CASH_FLOW',
                                'NOTES_FIXED_ASSETS','NOTES_RECEIVABLES','NOTES_DEBT',
                                'NOTES_OTHER','OTHER'
                            )),
    zone_label              TEXT,
    bounding_box            JSONB NOT NULL DEFAULT '{"x":0,"y":0,"width":1,"height":1}',
    confidence_score        FLOAT NOT NULL DEFAULT 0.0,
    classification_method   TEXT CHECK (classification_method IN ('VLM','HEURISTIC','ML','COMBINED')),
    account_column          INT,
    value_columns           INT[] DEFAULT '{}',
    header_rows             INT[] DEFAULT '{}',
    detected_periods        TEXT[] DEFAULT '{}',
    detected_currency       TEXT,
    detected_unit           TEXT,
    cell_data               JSONB,
    extracted_rows          JSONB,
    status                  TEXT NOT NULL DEFAULT 'DETECTED'
                            CHECK (status IN ('DETECTED','CONFIRMED','REJECTED','MANUAL')),
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_documents_tenant ON documents(tenant_id);
CREATE INDEX idx_documents_status ON documents(tenant_id, processing_status);
CREATE INDEX idx_documents_uploaded_by ON documents(uploaded_by);
CREATE INDEX idx_detected_zones_doc ON detected_zones(document_id);
CREATE INDEX idx_detected_zones_type ON detected_zones(document_id, zone_type);
```

### V005__model_templates.sql

```sql
CREATE TABLE model_templates (
    id                  UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id           UUID REFERENCES tenants(id),
    name                TEXT NOT NULL,
    framework           TEXT NOT NULL DEFAULT 'IFRS'
                        CHECK (framework IN ('IFRS','US_GAAP','ISLAMIC','CUSTOM')),
    description         TEXT,
    version             INT NOT NULL DEFAULT 1,
    is_default          BOOLEAN NOT NULL DEFAULT FALSE,
    source_template_id  UUID REFERENCES model_templates(id),
    status              TEXT NOT NULL DEFAULT 'ACTIVE'
                        CHECK (status IN ('ACTIVE','DRAFT','ARCHIVED')),
    created_by          UUID REFERENCES users(id),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE model_line_items (
    id                  UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    template_id         UUID NOT NULL REFERENCES model_templates(id) ON DELETE CASCADE,
    item_code           TEXT NOT NULL,
    parent_item_id      UUID REFERENCES model_line_items(id),
    zone_type           TEXT NOT NULL,
    label               TEXT NOT NULL,
    category            TEXT,
    item_type           TEXT NOT NULL
                        CHECK (item_type IN ('INPUT','FORMULA','VALIDATION','CATEGORY','MANUAL')),
    formula             TEXT,
    display_order       INT NOT NULL,
    indent_level        INT NOT NULL DEFAULT 0,
    is_total            BOOLEAN NOT NULL DEFAULT FALSE,
    is_required         BOOLEAN NOT NULL DEFAULT FALSE,
    is_hidden           BOOLEAN NOT NULL DEFAULT FALSE,
    sign_convention     TEXT NOT NULL DEFAULT 'NATURAL'
                        CHECK (sign_convention IN ('NATURAL','NEGATIVE','POSITIVE')),
    display_format      TEXT NOT NULL DEFAULT 'NUMBER'
                        CHECK (display_format IN ('NUMBER','PERCENTAGE','RATIO','DAYS','TIMES')),
    synonyms            TEXT[] NOT NULL DEFAULT '{}',
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE model_validations (
    id                  UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    template_id         UUID NOT NULL REFERENCES model_templates(id) ON DELETE CASCADE,
    name                TEXT NOT NULL,
    formula             TEXT NOT NULL,
    expected_value      NUMERIC NOT NULL DEFAULT 0,
    tolerance           NUMERIC NOT NULL DEFAULT 1,
    severity            TEXT NOT NULL DEFAULT 'ERROR'
                        CHECK (severity IN ('ERROR','WARNING')),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

ALTER TABLE model_line_items ADD CONSTRAINT uq_line_item_code
    UNIQUE (template_id, item_code);

CREATE INDEX idx_line_items_template ON model_line_items(template_id);
CREATE INDEX idx_line_items_zone ON model_line_items(template_id, zone_type);
CREATE INDEX idx_line_items_parent ON model_line_items(parent_item_id);
CREATE INDEX idx_validations_template ON model_validations(template_id);
```

### V006__spreading.sql

```sql
CREATE TABLE customer_models (
    id                  UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    customer_id         UUID NOT NULL REFERENCES customers(id),
    template_id         UUID NOT NULL REFERENCES model_templates(id),
    version             INT NOT NULL DEFAULT 1,
    customizations      JSONB NOT NULL DEFAULT '{}',
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE spread_items (
    id                  UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id           UUID NOT NULL REFERENCES tenants(id),
    customer_id         UUID NOT NULL REFERENCES customers(id),
    customer_model_id   UUID NOT NULL REFERENCES customer_models(id),
    document_id         UUID REFERENCES documents(id),
    statement_date      DATE NOT NULL,
    frequency           TEXT NOT NULL DEFAULT 'ANNUAL'
                        CHECK (frequency IN ('MONTHLY','QUARTERLY','SEMI_ANNUAL','ANNUAL')),
    audit_method        TEXT NOT NULL DEFAULT 'AUDITED'
                        CHECK (audit_method IN ('AUDITED','UNAUDITED','MANAGEMENT')),
    statement_type      TEXT NOT NULL DEFAULT 'ORIGINAL',
    source_currency     TEXT,
    target_currency     TEXT,
    consolidation       TEXT DEFAULT 'CONSOLIDATED'
                        CHECK (consolidation IN ('CONSOLIDATED','STANDALONE')),
    status              TEXT NOT NULL DEFAULT 'DRAFT'
                        CHECK (status IN ('DRAFT','SUBMITTED','APPROVED','REJECTED','PUSHED')),
    base_spread_id      UUID REFERENCES spread_items(id),
    locked_by           UUID REFERENCES users(id),
    locked_at           TIMESTAMPTZ,
    processing_time_ms  INT,
    mapping_coverage    FLOAT,
    unit_scale          NUMERIC NOT NULL DEFAULT 1,
    current_version     INT NOT NULL DEFAULT 0,
    created_by          UUID NOT NULL REFERENCES users(id),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE spread_values (
    id                  UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    spread_item_id      UUID NOT NULL REFERENCES spread_items(id) ON DELETE CASCADE,
    line_item_id        UUID NOT NULL REFERENCES model_line_items(id),
    item_code           TEXT NOT NULL,
    mapped_value        NUMERIC,
    raw_value           NUMERIC,
    expression_type     TEXT NOT NULL DEFAULT 'DIRECT'
                        CHECK (expression_type IN (
                            'DIRECT','SUM','NEGATE','ABSOLUTE','SCALE','MANUAL','FORMULA'
                        )),
    expression_detail   JSONB,
    scale_factor        NUMERIC NOT NULL DEFAULT 1,
    confidence_score    FLOAT,
    confidence_level    TEXT CHECK (confidence_level IN ('HIGH','MEDIUM','LOW')),
    source_page         INT,
    source_text         TEXT,
    source_coordinates  JSONB,
    is_manual_override  BOOLEAN NOT NULL DEFAULT FALSE,
    is_autofilled       BOOLEAN NOT NULL DEFAULT FALSE,
    is_formula_cell     BOOLEAN NOT NULL DEFAULT FALSE,
    override_comment    TEXT,
    model_version       TEXT,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE spread_versions (
    id                  UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    spread_item_id      UUID NOT NULL REFERENCES spread_items(id) ON DELETE CASCADE,
    version_number      INT NOT NULL,
    snapshot_data       JSONB NOT NULL,
    action              TEXT NOT NULL
                        CHECK (action IN (
                            'CREATED','SAVED','SUBMITTED','APPROVED',
                            'REJECTED','ROLLED_BACK','OVERRIDDEN'
                        )),
    comments            TEXT,
    diff_summary        JSONB,
    cells_changed       INT DEFAULT 0,
    created_by          UUID NOT NULL REFERENCES users(id),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (spread_item_id, version_number)
);

CREATE TABLE expression_patterns (
    id                  UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id           UUID NOT NULL REFERENCES tenants(id),
    customer_id         UUID NOT NULL REFERENCES customers(id),
    line_item_code      TEXT NOT NULL,
    zone_type           TEXT NOT NULL,
    expression_type     TEXT NOT NULL,
    source_labels       TEXT[] NOT NULL,
    scale_factor        NUMERIC NOT NULL DEFAULT 1,
    confidence          FLOAT,
    usage_count         INT NOT NULL DEFAULT 1,
    last_used_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (tenant_id, customer_id, line_item_code)
);

CREATE INDEX idx_spread_items_customer ON spread_items(customer_id);
CREATE INDEX idx_spread_items_tenant_status ON spread_items(tenant_id, status);
CREATE INDEX idx_spread_items_document ON spread_items(document_id);
CREATE INDEX idx_spread_values_spread ON spread_values(spread_item_id);
CREATE INDEX idx_spread_values_item ON spread_values(spread_item_id, line_item_id);
CREATE INDEX idx_spread_versions_spread ON spread_versions(spread_item_id);
CREATE INDEX idx_expression_patterns_lookup
    ON expression_patterns(tenant_id, customer_id, zone_type);
```

### V007__audit_event_log.sql

```sql
CREATE TABLE event_log (
    sequence_id     BIGSERIAL NOT NULL,
    id              UUID NOT NULL DEFAULT uuid_generate_v4(),
    tenant_id       UUID NOT NULL,

    event_type      TEXT NOT NULL,
    action          TEXT NOT NULL,

    actor_id        UUID NOT NULL,
    actor_email     TEXT NOT NULL,
    actor_role      TEXT,
    actor_ip        TEXT,

    entity_type     TEXT NOT NULL,
    entity_id       UUID NOT NULL,
    parent_entity_type TEXT,
    parent_entity_id   UUID,

    old_state       JSONB,
    new_state       JSONB,
    diff            JSONB,
    metadata        JSONB DEFAULT '{}',

    prev_hash       TEXT,
    event_hash      TEXT NOT NULL,

    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    PRIMARY KEY (sequence_id, created_at)
) PARTITION BY RANGE (created_at);

-- Initial partition
CREATE TABLE event_log_2026_q2 PARTITION OF event_log
    FOR VALUES FROM ('2026-04-01') TO ('2026-07-01');
CREATE TABLE event_log_2026_q3 PARTITION OF event_log
    FOR VALUES FROM ('2026-07-01') TO ('2026-10-01');
CREATE TABLE event_log_2026_q4 PARTITION OF event_log
    FOR VALUES FROM ('2026-10-01') TO ('2027-01-01');

-- Indexes
CREATE INDEX idx_event_log_entity
    ON event_log (entity_type, entity_id, created_at DESC);
CREATE INDEX idx_event_log_actor
    ON event_log (actor_id, created_at DESC);
CREATE INDEX idx_event_log_tenant_type
    ON event_log (tenant_id, event_type, created_at DESC);
CREATE INDEX idx_event_log_parent
    ON event_log (parent_entity_type, parent_entity_id, created_at DESC);

-- Immutability enforcement
CREATE OR REPLACE FUNCTION prevent_event_log_mutation()
RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION 'event_log is append-only. UPDATE and DELETE are forbidden.';
    RETURN NULL;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER enforce_immutability
    BEFORE UPDATE OR DELETE ON event_log
    FOR EACH ROW
    EXECUTE FUNCTION prevent_event_log_mutation();
```

### V008__seed_data.sql

```sql
-- Demo admin user (password: admin123 — BCrypt hash)
INSERT INTO users (id, tenant_id, email, password_hash, full_name, status) VALUES
    ('20000000-0000-0000-0000-000000000001',
     '00000000-0000-0000-0000-000000000001',
     'admin@numera.ai',
     '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy',
     'Admin User',
     'ACTIVE');

-- Demo analyst user (password: analyst123)
INSERT INTO users (id, tenant_id, email, password_hash, full_name, status) VALUES
    ('20000000-0000-0000-0000-000000000002',
     '00000000-0000-0000-0000-000000000001',
     'analyst@numera.ai',
     '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy',
     'Demo Analyst',
     'ACTIVE');

-- Assign roles
INSERT INTO user_roles (user_id, role_id) VALUES
    ('20000000-0000-0000-0000-000000000001', '10000000-0000-0000-0000-000000000001'),
    ('20000000-0000-0000-0000-000000000002', '10000000-0000-0000-0000-000000000002');

-- Demo customer
INSERT INTO customers (id, tenant_id, long_name, short_name, entity_id,
                       financial_year_end, source_currency, created_by) VALUES
    ('30000000-0000-0000-0000-000000000001',
     '00000000-0000-0000-0000-000000000001',
     'Emirates National Oil Company (ENOC)',
     'ENOC',
     'ENOC-001',
     '31 December',
     'AED',
     '20000000-0000-0000-0000-000000000001');
```

---

## 3. Shared Kernel — Key Classes

### 3.1 BaseEntity.kt

```kotlin
package com.numera.shared.domain

import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

@MappedSuperclass
abstract class BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "UUID")
    open var id: UUID? = null

    @Column(name = "created_at", nullable = false, updatable = false)
    open var createdAt: Instant = Instant.now()

    @Column(name = "updated_at", nullable = false)
    open var updatedAt: Instant = Instant.now()

    @PreUpdate
    fun onPreUpdate() { updatedAt = Instant.now() }
}

@MappedSuperclass
abstract class TenantAwareEntity : BaseEntity() {
    @Column(name = "tenant_id", nullable = false)
    open lateinit var tenantId: UUID
}
```

### 3.2 GlobalExceptionHandler.kt

```kotlin
package com.numera.shared.exception

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

data class ErrorResponse(
    val error: String,
    val message: String,
    val status: Int,
    val timestamp: String = java.time.Instant.now().toString(),
    val path: String? = null,
    val correlationId: String? = null,
)

open class ApiException(
    val errorCode: String,
    override val message: String,
    val httpStatus: HttpStatus = HttpStatus.BAD_REQUEST,
) : RuntimeException(message)

class NotFoundException(entity: String, id: Any)
    : ApiException("NOT_FOUND", "$entity not found: $id", HttpStatus.NOT_FOUND)

class ConflictException(msg: String)
    : ApiException("CONFLICT", msg, HttpStatus.CONFLICT)

class ForbiddenException(msg: String = "Access denied")
    : ApiException("FORBIDDEN", msg, HttpStatus.FORBIDDEN)

@RestControllerAdvice
class GlobalExceptionHandler {

    @ExceptionHandler(ApiException::class)
    fun handleApiException(ex: ApiException): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(ex.httpStatus).body(
            ErrorResponse(error = ex.errorCode, message = ex.message, status = ex.httpStatus.value())
        )

    @ExceptionHandler(Exception::class)
    fun handleGeneric(ex: Exception): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(500).body(
            ErrorResponse(error = "INTERNAL_ERROR", message = "Unexpected error", status = 500)
        )
}
```

### 3.3 AuditService.kt

```kotlin
package com.numera.shared.audit

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.security.MessageDigest
import java.time.Instant
import java.util.UUID

enum class AuditAction {
    CREATE, UPDATE, DELETE, SUBMIT, APPROVE, REJECT,
    ROLLBACK, LOCK, UNLOCK, LOGIN, MAP, CORRECT, ACCEPT, OVERRIDE, EXPORT
}

data class AuditEventRequest(
    val tenantId: UUID,
    val eventType: String,
    val action: AuditAction,
    val actorId: UUID,
    val actorEmail: String,
    val actorRole: String? = null,
    val actorIp: String? = null,
    val entityType: String,
    val entityId: UUID,
    val parentEntityType: String? = null,
    val parentEntityId: UUID? = null,
    val oldState: Any? = null,        // Will be serialized to JSONB
    val newState: Any? = null,
    val metadata: Map<String, Any>? = null,
)

@Service
class AuditService(
    private val eventLogRepo: EventLogRepository,
    private val objectMapper: ObjectMapper,
) {
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun record(event: AuditEventRequest) {
        val oldJson = event.oldState?.let { objectMapper.valueToTree<JsonNode>(it) }
        val newJson = event.newState?.let { objectMapper.valueToTree<JsonNode>(it) }
        val diffJson = computeDiff(oldJson, newJson)
        val metaJson = event.metadata?.let { objectMapper.valueToTree<JsonNode>(it) }

        val prevHash = eventLogRepo.findLatestHashForTenant(event.tenantId)
        val eventHash = computeHash(prevHash, event.eventType, event.entityId, newJson)

        eventLogRepo.insert(
            tenantId = event.tenantId,
            eventType = event.eventType,
            action = event.action.name,
            actorId = event.actorId,
            actorEmail = event.actorEmail,
            actorRole = event.actorRole,
            actorIp = event.actorIp,
            entityType = event.entityType,
            entityId = event.entityId,
            parentEntityType = event.parentEntityType,
            parentEntityId = event.parentEntityId,
            oldState = oldJson,
            newState = newJson,
            diff = diffJson,
            metadata = metaJson,
            prevHash = prevHash,
            eventHash = eventHash,
        )
    }

    private fun computeHash(prevHash: String?, eventType: String, entityId: UUID, state: JsonNode?): String {
        val payload = "${prevHash ?: "GENESIS"}|$eventType|$entityId|${state ?: "null"}|${Instant.now()}"
        return MessageDigest.getInstance("SHA-256")
            .digest(payload.toByteArray())
            .joinToString("") { "%02x".format(it) }
    }

    private fun computeDiff(old: JsonNode?, new: JsonNode?): JsonNode? {
        if (old == null || new == null) return null
        val diffs = mutableListOf<Map<String, Any?>>()
        val allFields = (old.fieldNames().asSequence() + new.fieldNames().asSequence()).toSet()
        for (field in allFields) {
            val oldVal = old.get(field)
            val newVal = new.get(field)
            if (oldVal != newVal) {
                diffs.add(mapOf("field" to field, "old" to oldVal, "new" to newVal))
            }
        }
        return if (diffs.isEmpty()) null else objectMapper.valueToTree(diffs)
    }
}
```

---

## 4. REST API Specifications (Complete)

### 4.1 Auth API

#### POST /api/auth/login

**Request Body:**
```json
{
  "email": "analyst@numera.ai",
  "password": "analyst123",
  "tenantName": "demo"
}
```

**Response 200:**
```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiIs...",
  "refreshToken": "dGhpcyBpcyBhIHJlZn...",
  "expiresIn": 900,
  "user": {
    "id": "20000000-...",
    "email": "analyst@numera.ai",
    "fullName": "Demo Analyst",
    "roles": ["ANALYST"],
    "tenantId": "00000000-..."
  }
}
```

**Error 401:**
```json
{ "error": "INVALID_CREDENTIALS", "message": "Invalid email or password", "status": 401 }
```

#### GET /api/auth/me  (Header: `Authorization: Bearer {token}`)

**Response 200:**
```json
{
  "id": "20000000-...",
  "email": "analyst@numera.ai",
  "fullName": "Demo Analyst",
  "roles": ["ANALYST"],
  "tenantId": "00000000-...",
  "tenantName": "demo",
  "lastLoginAt": "2026-04-10T09:00:00Z"
}
```

### 4.2 Document API

#### POST /api/documents/upload  (multipart/form-data)

**Form Fields:** `file` (binary), `language` (string, default "en")

**Response 202:**
```json
{
  "documentId": "uuid-...",
  "filename": "ENOC_Annual_Report_2025.pdf",
  "status": "UPLOADED",
  "message": "Document uploaded. Processing started."
}
```

#### GET /api/documents/{id}

**Response 200:**
```json
{
  "id": "uuid-...",
  "filename": "ENOC_Annual_Report_2025.pdf",
  "originalFilename": "ENOC_Annual_Report_2025.pdf",
  "fileType": "PDF",
  "fileSize": 5242880,
  "language": "en",
  "processingStatus": "READY",
  "pdfType": "NATIVE",
  "backendUsed": "native",
  "totalPages": 42,
  "processingTimeMs": 3200,
  "uploadedBy": "20000000-...",
  "uploadedByName": "Demo Analyst",
  "zonesDetected": 5,
  "createdAt": "2026-04-10T09:30:00Z"
}
```

#### GET /api/documents/{id}/zones

**Response 200:**
```json
{
  "documentId": "uuid-...",
  "zones": [
    {
      "id": "uuid-...",
      "pageNumber": 4,
      "zoneType": "INCOME_STATEMENT",
      "zoneLabel": "Consolidated Statement of Profit or Loss",
      "confidenceScore": 0.96,
      "classificationMethod": "VLM",
      "detectedPeriods": ["2025", "2024"],
      "detectedCurrency": "AED",
      "detectedUnit": "thousands",
      "status": "DETECTED",
      "rowCount": 28
    }
  ]
}
```

#### PUT /api/documents/{id}/zones/{zoneId}

**Request Body:**
```json
{
  "zoneType": "BALANCE_SHEET",
  "zoneLabel": "Statement of Financial Position",
  "status": "CONFIRMED"
}
```

**Response 200:** Updated zone object. **Audit event:** `ZONE_CORRECTED`

### 4.3 Model Template API

#### GET /api/model-templates

**Response 200:**
```json
{
  "templates": [
    {
      "id": "uuid-...",
      "name": "IFRS Corporate",
      "framework": "IFRS",
      "version": 1,
      "isDefault": true,
      "itemCount": 195,
      "status": "ACTIVE"
    }
  ]
}
```

#### GET /api/model-templates/{id}/items?zone=INCOME_STATEMENT

**Response 200:**
```json
{
  "templateId": "uuid-...",
  "templateName": "IFRS Corporate",
  "zone": "INCOME_STATEMENT",
  "items": [
    {
      "id": "uuid-...",
      "itemCode": "IS001",
      "label": "Revenue",
      "category": "Operating Revenue",
      "itemType": "INPUT",
      "formula": null,
      "displayOrder": 10,
      "indentLevel": 0,
      "isTotal": false,
      "isRequired": true,
      "signConvention": "NATURAL",
      "synonyms": ["Revenue", "Turnover", "Net Sales", "Total Revenue"]
    },
    {
      "id": "uuid-...",
      "itemCode": "IS002",
      "label": "Cost of Sales",
      "itemType": "INPUT",
      "signConvention": "NEGATIVE",
      "synonyms": ["Cost of Sales", "COGS", "Cost of Revenue"]
    },
    {
      "id": "uuid-...",
      "itemCode": "IS003",
      "label": "Gross Profit",
      "itemType": "FORMULA",
      "formula": "{IS001} + {IS002}",
      "isTotal": true
    }
  ]
}
```

### 4.4 Spreading API

#### POST /api/customers/{custId}/spread-items

**Request Body:**
```json
{
  "documentId": "uuid-...",
  "templateId": "uuid-...",
  "statementDate": "2025-12-31",
  "frequency": "ANNUAL",
  "auditMethod": "AUDITED",
  "sourceCurrency": "AED",
  "consolidation": "CONSOLIDATED"
}
```

**Response 201:**
```json
{
  "id": "uuid-...",
  "customerId": "uuid-...",
  "documentId": "uuid-...",
  "statementDate": "2025-12-31",
  "status": "DRAFT",
  "currentVersion": 0,
  "createdAt": "2026-04-10T10:00:00Z"
}
```

**Audit event:** `SPREAD_CREATED`

#### POST /api/spread-items/{id}/process

Triggers the full AI mapping pipeline. Returns mapping results.

**Response 200:**
```json
{
  "spreadItemId": "uuid-...",
  "processingTimeMs": 8500,
  "summary": {
    "totalItems": 120,
    "mapped": 95,
    "highConfidence": 72,
    "mediumConfidence": 15,
    "lowConfidence": 8,
    "unmapped": 25,
    "formulaComputed": 40,
    "autofilled": 12,
    "coveragePct": 79.2
  },
  "unitScale": 1000,
  "validations": [
    { "name": "Balance Sheet Check", "status": "PASS", "difference": 0 },
    { "name": "P&L Attribution", "status": "WARNING", "difference": 3.5 }
  ],
  "values": [
    {
      "id": "uuid-...",
      "itemCode": "IS001",
      "label": "Revenue",
      "mappedValue": 12500000,
      "rawValue": 12500,
      "expressionType": "SUM",
      "expressionDetail": {
        "sources": [
          { "label": "Product Sales", "value": 7200, "page": 4 },
          { "label": "Service Income", "value": 3100, "page": 4 },
          { "label": "Commission", "value": 2200, "page": 4 }
        ],
        "formula": "Product Sales + Service Income + Commission"
      },
      "scaleFactor": 1000,
      "confidenceScore": 0.92,
      "confidenceLevel": "HIGH",
      "sourcePage": 4,
      "sourceText": "Revenue from operations",
      "isManualOverride": false,
      "isAutofilled": false,
      "isFormulaCell": false
    }
  ]
}
```

**Audit events:** `SPREAD_VALUE_MAPPED_AI` (one per mapped value)

#### PUT /api/spread-items/{id}/values/{valueId}

**Request Body:**
```json
{
  "mappedValue": 12800000,
  "overrideComment": "Corrected: included intercompany elimination",
  "expressionType": "MANUAL"
}
```

**Response 200:** Updated value object.
**Audit event:** `SPREAD_VALUE_CORRECTED`

#### POST /api/spread-items/{id}/values/bulk-accept

**Request Body:**
```json
{
  "confidenceThreshold": "HIGH",
  "itemCodes": null
}
```

Accepts all values at or above the specified confidence level, or specific items if `itemCodes` provided.

**Response 200:** `{ "accepted": 72, "total": 95 }`
**Audit event:** `SPREAD_BULK_ACCEPTED`

#### POST /api/spread-items/{id}/submit

Validates all required cells are mapped, runs validation rules. Creates version snapshot.

**Request Body:**
```json
{
  "comments": "Q4 2025 annual spread - complete",
  "overrideValidationWarnings": false
}
```

**Response 200:**
```json
{
  "status": "SUBMITTED",
  "version": 2,
  "validations": [
    { "name": "Balance Sheet Check", "status": "PASS", "difference": 0 },
    { "name": "Cash Flow Reconciliation", "status": "PASS", "difference": 0 }
  ]
}
```

**Error 422 (validation failures):**
```json
{
  "error": "VALIDATION_FAILED",
  "message": "2 validation errors found",
  "validations": [
    { "name": "Balance Sheet Check", "status": "FAIL", "difference": -45000, "severity": "ERROR" }
  ],
  "unmappedRequired": ["IS015"]
}
```

**Audit event:** `SPREAD_SUBMITTED`

#### GET /api/spread-items/{id}/history

**Response 200:**
```json
{
  "spreadItemId": "uuid-...",
  "versions": [
    {
      "versionNumber": 3,
      "action": "SUBMITTED",
      "comments": "Q4 2025 annual spread",
      "cellsChanged": 3,
      "createdBy": "Demo Analyst",
      "createdAt": "2026-04-10T10:30:00Z"
    },
    {
      "versionNumber": 2,
      "action": "SAVED",
      "comments": null,
      "cellsChanged": 15,
      "createdBy": "Demo Analyst",
      "createdAt": "2026-04-10T10:15:00Z"
    },
    {
      "versionNumber": 1,
      "action": "CREATED",
      "comments": "AI mapping completed",
      "cellsChanged": 95,
      "createdBy": "System",
      "createdAt": "2026-04-10T10:00:00Z"
    }
  ]
}
```

#### GET /api/spread-items/{id}/diff/{v1}/{v2}

**Response 200:**
```json
{
  "fromVersion": 1,
  "toVersion": 3,
  "changes": [
    {
      "itemCode": "IS001",
      "label": "Revenue",
      "oldValue": 12500000,
      "newValue": 12800000,
      "changeType": "MODIFIED",
      "modifiedBy": "Demo Analyst"
    },
    {
      "itemCode": "IS015",
      "label": "Other Income",
      "oldValue": null,
      "newValue": 350000,
      "changeType": "ADDED",
      "modifiedBy": "Demo Analyst"
    }
  ],
  "totalAdded": 3,
  "totalModified": 2,
  "totalRemoved": 0
}
```

#### POST /api/spread-items/{id}/rollback/{version}

**Request Body:** `{ "comments": "Rolling back to pre-correction state" }`

**Response 200:** `{ "status": "DRAFT", "currentVersion": 4, "restoredFromVersion": 1 }`
**Audit event:** `SPREAD_ROLLED_BACK`

### 4.5 Audit API

#### GET /api/audit/events?entityType=spread_item&entityId={id}

**Response 200:**
```json
{
  "events": [
    {
      "id": "uuid-...",
      "eventType": "SPREAD_VALUE_CORRECTED",
      "action": "CORRECT",
      "actorEmail": "analyst@numera.ai",
      "entityType": "spread_value",
      "entityId": "uuid-...",
      "parentEntityType": "spread_item",
      "parentEntityId": "uuid-...",
      "diff": [
        { "field": "mapped_value", "old": 12500000, "new": 12800000 }
      ],
      "createdAt": "2026-04-10T10:20:00Z"
    }
  ],
  "total": 15,
  "page": 0,
  "size": 50
}
```

#### GET /api/audit/verify/{tenantId}

**Response 200:**
```json
{
  "status": "VALID",
  "eventsVerified": 1547,
  "chainStartedAt": "2026-04-01T00:00:00Z",
  "lastEventAt": "2026-04-10T10:30:00Z",
  "verificationTimeMs": 450
}
```

---

## 5. ML Service Client (Complete Integration)

### MlServiceClient.kt

The backend calls 5 ML endpoints. Each call is a blocking HTTP request on a Virtual Thread:

```kotlin
package com.numera.document.infrastructure

import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitBody
import com.numera.shared.config.NumeraProperties

@Service
class MlServiceClient(
    webClientBuilder: WebClient.Builder,
    private val config: NumeraProperties,
) {
    private val ocrClient = webClientBuilder
        .baseUrl(config.ml.ocrServiceUrl)
        .build()

    private val mlClient = webClientBuilder
        .baseUrl(config.ml.mlServiceUrl)
        .build()

    // ── 1. OCR Text Extraction ──
    // POST http://ocr-service:8001/api/ocr/extract
    data class OcrRequest(val document_id: String, val storage_path: String, val language: String = "en")
    data class OcrTextBlock(val text: String, val confidence: Float, val bbox: List<Float>, val page: Int)
    data class OcrResponse(
        val document_id: String, val total_pages: Int, val pages_processed: Int,
        val text_blocks: List<OcrTextBlock>, val full_text: String,
        val processing_time_ms: Int, val backend: String, val pdf_type: String,
    )

    fun extractText(documentId: String, storagePath: String, language: String = "en"): OcrResponse =
        ocrClient.post().uri("/ocr/extract")
            .bodyValue(OcrRequest(documentId, storagePath, language))
            .retrieve().bodyToMono(OcrResponse::class.java).block()!!

    // ── 2. Table Detection ──
    // POST http://ocr-service:8001/api/ocr/tables/detect
    data class TableDetectRequest(val document_id: String, val storage_path: String)
    data class TableDetectResponse(
        val document_id: String, val total_pages: Int,
        val tables_detected: Int, val tables: List<Map<String, Any>>,
        val processing_time_ms: Int, val backend: String, val pdf_type: String,
    )

    fun detectTables(documentId: String, storagePath: String): TableDetectResponse =
        ocrClient.post().uri("/ocr/tables/detect")
            .bodyValue(TableDetectRequest(documentId, storagePath))
            .retrieve().bodyToMono(TableDetectResponse::class.java).block()!!

    // ── 3. Zone Classification ──
    // POST http://ml-service:8002/api/ml/zones/classify
    data class ZoneClassifyRequest(val document_id: String, val tables: List<Map<String, Any>>)
    data class ClassifiedZone(
        val table_id: String, val zone_type: String, val zone_label: String,
        val confidence: Float, val classification_method: String,
        val detected_periods: List<String>, val detected_currency: String?,
        val detected_unit: String?,
    )
    data class ZoneClassifyResponse(
        val document_id: String, val zones: List<ClassifiedZone>, val processing_time_ms: Int,
    )

    fun classifyZones(documentId: String, tables: List<Map<String, Any>>): ZoneClassifyResponse =
        mlClient.post().uri("/ml/zones/classify")
            .bodyValue(ZoneClassifyRequest(documentId, tables))
            .retrieve().bodyToMono(ZoneClassifyResponse::class.java).block()!!

    // ── 4. Semantic Mapping ──
    // POST http://ml-service:8002/api/ml/mapping/suggest
    data class MappingSuggestRequest(
        val document_id: String,
        val source_rows: List<Map<String, Any>>,
        val target_items: List<Map<String, Any>>,
        val tenant_id: String? = null,
    )
    data class MappingSuggestResponse(
        val document_id: String,
        val mappings: List<Map<String, Any>>,
        val summary: Map<String, Int>,
        val processing_time_ms: Int,
    )

    fun suggestMappings(request: MappingSuggestRequest): MappingSuggestResponse =
        mlClient.post().uri("/ml/mapping/suggest")
            .bodyValue(request)
            .retrieve().bodyToMono(MappingSuggestResponse::class.java).block()!!

    // ── 5. Expression Building ──
    // POST http://ml-service:8002/api/ml/expressions/build
    data class ExpressionBuildRequest(
        val document_id: String, val tenant_id: String, val customer_id: String,
        val template_id: String, val zone_type: String, val period_index: Int = 0,
        val extracted_rows: List<Map<String, Any>>,
        val semantic_matches: List<Map<String, Any>>,
        val use_autofill: Boolean = true,
    )
    data class ExpressionBuildResponse(
        val document_id: String, val template_id: String, val zone_type: String,
        val expressions: List<Map<String, Any>>,
        val total_mapped: Int, val total_items: Int, val coverage_pct: Float,
        val unit_scale: Float, val autofilled: Int,
        val validation_results: List<Map<String, Any>>,
    )

    fun buildExpressions(request: ExpressionBuildRequest): ExpressionBuildResponse =
        mlClient.post().uri("/ml/expressions/build")
            .bodyValue(request)
            .retrieve().bodyToMono(ExpressionBuildResponse::class.java).block()!!

    // ── 6. Feedback ──
    // POST http://ml-service:8002/api/ml/feedback
    data class FeedbackItem(
        val source_text: String, val source_zone_type: String,
        val suggested_item_id: String, val corrected_item_id: String,
        val correction_type: String, val document_id: String,
        val customer_id: String?, val tenant_id: String?,
    )
    data class FeedbackRequest(val corrections: List<FeedbackItem>)
    data class FeedbackResponse(val accepted: Int, val total_stored: Int)

    fun submitFeedback(corrections: List<FeedbackItem>): FeedbackResponse =
        mlClient.post().uri("/ml/feedback")
            .bodyValue(FeedbackRequest(corrections))
            .retrieve().bodyToMono(FeedbackResponse::class.java).block()!!
}
```

---

## 6. MappingOrchestrator — Step-by-Step Pipeline

```kotlin
package com.numera.spreading.application

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class MappingOrchestrator(
    private val mlClient: MlServiceClient,
    private val formulaEngine: FormulaEngine,
    private val templateService: TemplateService,
    private val spreadValueRepo: SpreadValueRepository,
    private val expressionPatternRepo: ExpressionPatternRepository,
    private val auditService: AuditService,
) {
    /**
     * Full pipeline: Document → AI Mapping → Formulas → Validations → Save
     *
     * Steps:
     * 1. Load template and detected zones from DB
     * 2. For each zone: call ML semantic matching
     * 3. For each zone: call ML expression building (with autofill from patterns)
     * 4. Save all mapped values to spread_values table
     * 5. Evaluate all FORMULA cells using FormulaEngine
     * 6. Run all VALIDATION rules
     * 7. Persist expression patterns for future autofill
     * 8. Create version snapshot
     * 9. Record audit events
     * 10. Return mapping result
     */
    @Transactional
    fun processSpread(spreadItem: SpreadItem, document: Document): MappingResult {
        // Implementation follows the 10 steps above
        // Each step maps to a specific ML service call + DB operation
        // See response body format in Section 4.4 POST /api/spread-items/{id}/process
    }
}
```

---

## 7. FormulaEngine — Evaluation Rules

```
Formula DSL Grammar:
  formula      = expression
  expression   = term (('+' | '-') term)*
  term         = factor (('*' | '/') factor)*
  factor       = NUMBER | '{ITEM_CODE}' | function | '(' expression ')' | '-' factor
  function     = 'ABS(' expression ')' | 'SUM(' range ')' | 'IF(' condition ',' expr ',' expr ')'
  range        = '{CODE}' ':' '{CODE}'
  condition    = expression ('<' | '>' | '=' | '<=' | '>=') expression

Examples:
  {IS001} + {IS002}                           → Revenue + Cost of Sales
  ABS({IS002})                                → Absolute value of COGS
  {IS001} / {IS002} * 100                     → Gross Margin %
  SUM({BS001}:{BS007})                        → Sum of current asset items
  IF({BS001} > 0, {BS001} / {BS050}, 0)       → Current Ratio if assets > 0

Rules:
  - If ANY referenced cell is NULL → formula result is NULL
  - Division by zero → NULL (not error)
  - All arithmetic uses BigDecimal (scale=4, HALF_UP rounding)
```

---

## 8. State Machines

### Document Processing Status

```
UPLOADED → PROCESSING → OCR_COMPLETE → TABLES_DETECTED → ZONES_CLASSIFIED → READY
    │                                                                           │
    └──────────────────────── ERROR ◄───────────────────────────────────────────┘
```

### Spread Item Status

```
                ┌──────── REJECTED ◄────────┐
                │                            │
DRAFT ──► SUBMITTED ──► APPROVED ──► PUSHED
  ▲          │              │
  │          │              │
  └──────────┘              │
  (reject)                  │
  ▲                         │
  └── ROLLED_BACK ◄─────────┘
```

---

## 9. Implementation Order

Execute in this exact order. Each step depends on the previous ones.

| Step | What | Files | Depends On |
|------|------|-------|------------|
| 1 | Project init + build.gradle.kts | `build.gradle.kts`, `settings.gradle.kts`, `NumeraApplication.kt` | Nothing |
| 2 | Docker Compose + application.yml | `docker-compose.yml`, `application.yml` | Step 1 |
| 3 | Shared kernel: BaseEntity, exceptions, config | `shared/**` | Step 1 |
| 4 | Flyway migrations V001-V008 | `db/migration/**` | Step 2, 3 |
| 5 | Auth module: User entity, JWT, login API | `auth/**` | Step 3, 4 |
| 6 | SecurityConfig + JWT filter | `shared/security/**`, `shared/config/SecurityConfig.kt` | Step 5 |
| 7 | Audit system: AuditService, EventLogRepository | `shared/audit/**` | Step 4, 6 |
| 8 | Customer module: entity, CRUD, search | `customer/**` | Step 6, 7 |
| 9 | Document module: entity, MinIO client, upload API | `document/**` (without ML integration) | Step 6, 7 |
| 10 | ML Service Client | `document/infrastructure/MlServiceClient.kt` | Step 9 |
| 11 | DocumentProcessingService (full pipeline) | `document/application/DocumentProcessingService.kt` | Step 10 |
| 12 | Model Template module: entities, CRUD API | `model/**` | Step 6, 7 |
| 13 | FormulaEngine | `model/application/FormulaEngine.kt` | Step 12 |
| 14 | Spreading module: entities, CRUD | `spreading/**` (without ML) | Step 8, 9, 12 |
| 15 | MappingOrchestrator | `spreading/application/MappingOrchestrator.kt` | Step 10, 13, 14 |
| 16 | SpreadVersionService (versioning + diff) | `spreading/application/SpreadVersionService.kt` | Step 14, 7 |
| 17 | AutofillService | `spreading/application/AutofillService.kt` | Step 15 |
| 18 | Dashboard API | Simple stats endpoint | Step 14, 8, 9 |
| 19 | Module boundary tests | `ModuleBoundaryTest.kt` | All |
| 20 | Integration tests | Test files | All |

---

## 10. Testing Specifications

### ModuleBoundaryTest.kt

```kotlin
package com.numera

import org.junit.jupiter.api.Test
import org.springframework.modulith.core.ApplicationModules

class ModuleBoundaryTest {
    @Test
    fun `verify module boundaries are not violated`() {
        val modules = ApplicationModules.of(NumeraApplication::class.java)
        modules.verify()
    }
}
```

### FormulaEngineTest.kt (Key Test Cases)

```kotlin
@Test fun `simple addition`() = assertEquals(300.toBigDecimal(), engine.evaluate("{A} + {B}", mapOf("A" to 100.bd, "B" to 200.bd)))
@Test fun `subtraction with negatives`() = assertEquals((-50).toBigDecimal(), engine.evaluate("{A} + {B}", mapOf("A" to 100.bd, "B" to (-150).bd)))
@Test fun `null propagation`() = assertNull(engine.evaluate("{A} + {B}", mapOf("A" to 100.bd, "B" to null)))
@Test fun `division by zero returns null`() = assertNull(engine.evaluate("{A} / {B}", mapOf("A" to 100.bd, "B" to BigDecimal.ZERO)))
@Test fun `ABS function`() = assertEquals(100.toBigDecimal(), engine.evaluate("ABS({A})", mapOf("A" to (-100).bd)))
@Test fun `percentage calculation`() = assertEquals(25.toBigDecimal(), engine.evaluate("{A} / {B} * 100", mapOf("A" to 250.bd, "B" to 1000.bd)))
@Test fun `balance sheet validation`() { /* assets - liabilities - equity = 0 */ }
```

### Performance Benchmarks

```kotlin
@Test fun `login responds under 200ms`() { /* time POST /api/auth/login */ }
@Test fun `document upload 10MB under 2s`() { /* time POST /api/documents/upload */ }
@Test fun `template load under 100ms`() { /* time GET /api/model-templates/{id} */ }
@Test fun `spread history query under 300ms`() { /* time GET /api/spread-items/{id}/history */ }
```
