# Numera Platform — Comprehensive Gap & Implementation Plan

**Date:** April 13, 2026  
**Scope:** Full codebase audit across backend, frontend, ML service, OCR service, infrastructure  
**Format:** Independent chunks for AI agent execution

---

## Table of Contents

1. [Gap Summary Dashboard](#1-gap-summary-dashboard)
2. [CHUNK 1 — Critical Security Fixes](#chunk-1--critical-security-fixes)
3. [CHUNK 2 — Frontend XSS & Auth Hardening](#chunk-2--frontend-xss--auth-hardening)
4. [CHUNK 3 — Backend Code Quality & Performance](#chunk-3--backend-code-quality--performance)
5. [CHUNK 4 — API Design & Validation Gaps](#chunk-4--api-design--validation-gaps)
6. [CHUNK 5 — Database & Migration Gaps](#chunk-5--database--migration-gaps)
7. [CHUNK 6 — Backend Test Coverage](#chunk-6--backend-test-coverage)
8. [CHUNK 7 — Frontend Test Infrastructure](#chunk-7--frontend-test-infrastructure)
9. [CHUNK 8 — ML Service Hardening](#chunk-8--ml-service-hardening)
10. [CHUNK 9 — OCR Service Hardening](#chunk-9--ocr-service-hardening)
11. [CHUNK 10 — Reporting Module Implementation](#chunk-10--reporting-module-implementation)
12. [CHUNK 11 — Infrastructure & DevOps](#chunk-11--infrastructure--devops)
13. [CHUNK 12 — Frontend Functional Gaps](#chunk-12--frontend-functional-gaps)
14. [CHUNK 13 — ML Pipeline Improvements](#chunk-13--ml-pipeline-improvements)
15. [CHUNK 14 — Observability & Monitoring](#chunk-14--observability--monitoring)
16. [CHUNK 15 — Dependency & Supply Chain Security](#chunk-15--dependency--supply-chain-security)

---

## 1. Gap Summary Dashboard

| Category | Severity | Gap Count | Chunks |
|----------|----------|-----------|--------|
| **Security — Critical** | 🔴 P0 | 11 | 1, 2 |
| **Code Quality** | 🟠 P1 | 14 | 3, 4 |
| **Test Coverage** | 🔴 P0 | 8 | 6, 7 |
| **Database** | 🟠 P1 | 6 | 5 |
| **Functional Gaps** | 🟠 P1 | 12 | 10, 12 |
| **ML/OCR Hardening** | 🟡 P2 | 9 | 8, 9, 13 |
| **Infrastructure** | 🟡 P2 | 10 | 11, 14, 15 |
| **Total** | | **70** | **15 chunks** |

---

## CHUNK 1 — Critical Security Fixes

**Priority:** 🔴 P0 (Start immediately)  
**Scope:** Backend security vulnerabilities  
**Dependencies:** None  
**Estimated Files Modified:** 8-10

### 1.1 JWT Token Revocation/Blacklist

**Gap:** Logout does not invalidate JWT tokens. Tokens remain valid until expiry.  
**Risk:** OWASP A07:2021 — Identification and Authentication Failures  
**Files:**
- `backend/src/main/kotlin/com/numera/auth/application/AuthService.kt`
- `backend/src/main/kotlin/com/numera/shared/security/JwtTokenProvider.kt`
- `backend/src/main/kotlin/com/numera/shared/config/SecurityConfig.kt`

**Implementation Steps:**
1. In `JwtTokenProvider.kt`, add a method `blacklistToken(token: String, ttl: Duration)` that stores the token hash in Redis with TTL equal to remaining token lifetime.
2. In `JwtTokenProvider.isValid()`, add check: `if (redisTemplate.hasKey("jwt:blacklist:${sha256(token)}")) return false`.
3. In `AuthService.kt` logout method, call `jwtTokenProvider.blacklistToken(currentAccessToken, remainingTtl)`.
4. Also blacklist on password change and MFA disable operations.
5. Add a new migration or Redis key namespace: `numera:jwt:blacklist:{hash}`.

**Validation:**
- Write test: after logout, using same token returns 401.
- Write test: token blacklist entry expires after token TTL.

---

### 1.2 Filename Sanitization Hardening

**Gap:** `DocumentProcessingService.kt` (line ~361) does not prevent double extensions, does not limit filename length, does not validate MIME type vs extension.  
**Risk:** OWASP A03:2021 — Injection (path traversal, arbitrary file write)  
**Files:**
- `backend/src/main/kotlin/com/numera/document/application/DocumentProcessingService.kt`

**Implementation Steps:**
1. Replace the current `sanitizeFilename` method:
```kotlin
private fun sanitizeFilename(name: String): String {
    val basename = name.substringAfterLast('/').substringAfterLast('\\')
    // Allow only alphanumeric, dots, hyphens, underscores
    val cleaned = basename.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        .replace("..", "_")
        .take(255)
        .trim()
    return cleaned.ifBlank { "document.pdf" }
}
```
2. Add MIME-type validation method:
```kotlin
private val ALLOWED_EXTENSIONS = mapOf(
    "application/pdf" to listOf(".pdf"),
    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" to listOf(".xlsx"),
    "application/vnd.openxmlformats-officedocument.wordprocessingml.document" to listOf(".docx"),
    "image/png" to listOf(".png"),
    "image/jpeg" to listOf(".jpg", ".jpeg"),
)

private fun validateFileExtension(filename: String, contentType: String?) {
    val allowedExts = ALLOWED_EXTENSIONS[contentType] ?: throw ApiException(
        ErrorCode.VALIDATION_ERROR, "Unsupported file type: $contentType"
    )
    if (allowedExts.none { filename.lowercase().endsWith(it) }) {
        throw ApiException(ErrorCode.VALIDATION_ERROR, "File extension does not match content type")
    }
}
```
3. Call `validateFileExtension` before storing any uploaded file.

**Validation:**
- Write tests for: double extensions rejected, path traversal sequences cleaned, MIME mismatch rejected, filename length > 255 truncated.

---

### 1.3 Remove Default MinIO Credentials from Code

**Gap:** `NumeraProperties.kt` (lines ~42-43) has `minioadmin`/`minioadmin` as defaults.  
**Risk:** OWASP A07:2021 — Identification and Authentication Failures  
**Files:**
- `backend/src/main/kotlin/com/numera/shared/config/NumeraProperties.kt`
- `backend/src/main/resources/application.yml`
- `backend/src/main/resources/application-dev.yml`

**Implementation Steps:**
1. In `NumeraProperties.kt`, remove default values from `Storage.accessKey` and `Storage.secretKey`:
```kotlin
data class Storage(
    val endpoint: String = "http://localhost:9000",
    val bucket: String = "numera-documents",
    val accessKey: String = "",
    val secretKey: String = "",
)
```
2. Add startup validation in the `@PostConstruct` or config validator:
```kotlin
@PostConstruct
fun validateStorageConfig() {
    if (config.storage.accessKey.isBlank() || config.storage.secretKey.isBlank()) {
        if (!config.storage.endpoint.contains("localhost")) {
            throw IllegalStateException("MINIO_ACCESS_KEY and MINIO_SECRET_KEY must be set for non-local deployments")
        }
        logger.warn("MinIO using empty credentials — acceptable only for local development")
    }
}
```
3. Move dev credentials into `application-dev.yml` only.

**Validation:**
- App fails to start with empty credentials when not using localhost endpoint.
- Dev profile still works with docker-compose defaults.

---

### 1.4 Add Password Validation to LoginRequest

**Gap:** `LoginRequest.kt` has no `@Size` constraint on password field — any length accepted.  
**Risk:** Weak password acceptance at login endpoint.  
**Files:**
- `backend/src/main/kotlin/com/numera/auth/dto/LoginRequest.kt`

**Implementation Steps:**
1. Add `@field:Size(min = 8, max = 128)` to the `password` field.
2. Ensure `GlobalExceptionHandler` properly returns 400 for `MethodArgumentNotValidException`.

**Validation:**
- Test: login with 3-char password returns 400.
- Test: login with 129-char password returns 400.

---

### 1.5 Rate Limiting on Auth Endpoints

**Gap:** No rate limiting on login, registration, MFA endpoints — brute force possible.  
**Risk:** OWASP A07:2021 — Identification and Authentication Failures  
**Files:**
- `backend/build.gradle.kts` (add bucket4j dependency)
- `backend/src/main/kotlin/com/numera/shared/config/RateLimitConfig.kt` (new)
- `backend/src/main/kotlin/com/numera/shared/security/RateLimitFilter.kt` (new)

**Implementation Steps:**
1. Add dependency: `implementation("com.bucket4j:bucket4j-core:8.10.1")`.
2. Create `RateLimitFilter` as a `OncePerRequestFilter`:
   - For `/api/auth/login`: 5 attempts per minute per IP.
   - For `/api/auth/register`: 3 attempts per minute per IP.
   - For `/api/auth/mfa/*`: 10 attempts per minute per IP.
   - Return `429 Too Many Requests` with `Retry-After` header.
3. Register the filter in `SecurityConfig` before the JWT filter.
4. Use Redis-backed buckets via `bucket4j-redis` for distributed deployments.

**Validation:**
- Test: 6th login attempt within 60s returns 429.
- Test: rate limit resets after window expires.

---

### 1.6 Sensitive Data Logging Prevention

**Gap:** Multiple services log request parameters without filtering PII/secrets.  
**Risk:** OWASP A09:2021 — Security Logging and Monitoring Failures  
**Files:**
- `backend/src/main/kotlin/com/numera/shared/audit/AuditService.kt`
- `backend/src/main/kotlin/com/numera/spreading/application/SpreadService.kt`

**Implementation Steps:**
1. Create a `LogSanitizer` utility:
```kotlin
object LogSanitizer {
    private val SENSITIVE_PATTERNS = listOf(
        Regex("\"password\"\\s*:\\s*\"[^\"]*\"") to "\"password\":\"***\"",
        Regex("\"secretKey\"\\s*:\\s*\"[^\"]*\"") to "\"secretKey\":\"***\"",
        Regex("\"accessToken\"\\s*:\\s*\"[^\"]*\"") to "\"accessToken\":\"***\"",
    )
    fun sanitize(input: String): String {
        var result = input
        SENSITIVE_PATTERNS.forEach { (pattern, replacement) ->
            result = result.replace(pattern, replacement)
        }
        return result
    }
}
```
2. Apply `LogSanitizer.sanitize()` to any `diffJson` or request body logging.

**Validation:**
- Test: sanitizer replaces password fields in JSON strings.
- Test: non-sensitive fields remain unmodified.

---

## CHUNK 2 — Frontend XSS & Auth Hardening

**Priority:** 🔴 P0 (Start immediately)  
**Scope:** Frontend security vulnerabilities  
**Dependencies:** None  
**Estimated Files Modified:** 10-12

### 2.1 Remove Hardcoded Demo Credentials

**Gap:** `src/app/(auth)/login/page.tsx` (lines 29-32) pre-fills `analyst@numera.ai` / `Password123!`.  
**Risk:** Credential exposure in deployed builds.  
**Files:**
- `numera-ui/src/app/(auth)/login/page.tsx`

**Implementation Steps:**
1. Change `defaultValues` to:
```typescript
defaultValues: {
    email: '',
    password: '',
}
```
2. If demo mode is needed, gate it behind `NEXT_PUBLIC_DEMO_MODE=true` and only populate defaults when that flag is set.

**Validation:**
- Visual check: login form fields are empty on page load.
- Verify no credentials in compiled JS bundle (`grep -r "Password123" .next/`).

---

### 2.2 Fix All XSS Vulnerabilities (dangerouslySetInnerHTML)

**Gap:** Multiple components render unsanitized HTML from user input.  
**Risk:** OWASP A03:2021 — Injection (Stored XSS)  
**Files:**
- `numera-ui/src/components/covenant/TemplateEditor.tsx` (lines 32, 46, 73, 220)
- `numera-ui/src/components/covenant/LetterPreview.tsx` (line ~90)
- `numera-ui/src/components/copilot/CopilotMessage.tsx` (line ~53)

**Implementation Steps:**
1. Install DOMPurify: `npm install dompurify @types/dompurify`.
2. Create a shared sanitizer utility:
```typescript
// src/utils/sanitize.ts
import DOMPurify from 'dompurify';

export function sanitizeHtml(dirty: string): string {
    return DOMPurify.sanitize(dirty, {
        ALLOWED_TAGS: ['b', 'i', 'em', 'strong', 'a', 'p', 'br', 'ul', 'ol', 'li', 'span', 'div', 'table', 'tr', 'td', 'th', 'thead', 'tbody', 'h1', 'h2', 'h3', 'h4', 'code', 'pre'],
        ALLOWED_ATTR: ['href', 'target', 'class', 'style'],
    });
}
```
3. Replace all `dangerouslySetInnerHTML={{ __html: content }}` with `dangerouslySetInnerHTML={{ __html: sanitizeHtml(content) }}`.
4. In `TemplateEditor.tsx`, sanitize before assigning to `innerHTML`:
```typescript
editorRef.current.innerHTML = sanitizeHtml(value);
```
5. In `CopilotMessage.tsx`, sanitize the markdown-formatted output.

**Validation:**
- Test: `<script>alert('xss')</script>` is stripped from rendered output.
- Test: valid formatting tags (bold, italic, links) still render.
- Test: `<img onerror="alert('xss')" src="x">` is stripped.

---

### 2.3 Migrate Token Storage from localStorage to httpOnly Cookies

**Gap:** `authStore.ts` stores JWT in localStorage, vulnerable to XSS token theft.  
**Risk:** OWASP A07:2021 — Identification and Authentication Failures  
**Files:**
- `numera-ui/src/stores/authStore.ts`
- `numera-ui/src/services/api.ts`
- `numera-ui/src/services/authApi.ts`
- `numera-ui/src/middleware.ts`
- `backend/src/main/kotlin/com/numera/auth/api/AuthController.kt` (backend support)

**Implementation Steps:**

**Backend Changes:**
1. Modify `AuthController.kt` login response to set httpOnly cookies:
```kotlin
@PostMapping("/login")
fun login(@Valid @RequestBody request: LoginRequest, response: HttpServletResponse): ResponseEntity<LoginResponse> {
    val result = authService.login(request)
    val cookie = ResponseCookie.from("numera_access", result.accessToken)
        .httpOnly(true)
        .secure(true)
        .sameSite("Strict")
        .path("/")
        .maxAge(Duration.ofMillis(config.jwt.accessExpirationMs))
        .build()
    response.addHeader("Set-Cookie", cookie.toString())
    // Return user info but NOT the token in body
    return ResponseEntity.ok(LoginResponse(user = result.user, mfaRequired = result.mfaRequired))
}
```
2. Modify JWT filter to read from cookie when Authorization header is absent.

**Frontend Changes:**
3. Remove `persist` middleware from `authStore.ts` for tokens. Only persist `user` object.
4. Update `api.ts` to rely on cookie-based auth (remove Authorization header injection).
5. Update `middleware.ts` to read from the httpOnly cookie for SSR auth checks.
6. Update logout to call backend (which clears the cookie).

**Validation:**
- Test: tokens not visible in `localStorage`.
- Test: `document.cookie` does not show `numera_access` (httpOnly).
- Test: API calls still authenticated via cookie.
- Test: logout clears cookie.

---

### 2.4 Add CSRF Protection

**Gap:** No CSRF token in frontend requests; backend CSRF disabled.  
**Risk:** OWASP A01:2021 — Broken Access Control (CSRF)  
**Files:**
- `numera-ui/src/services/api.ts`
- `backend/src/main/kotlin/com/numera/shared/config/SecurityConfig.kt`

**Implementation Steps:**
1. **Backend:** Enable CSRF with cookie-based token:
```kotlin
csrf {
    csrfTokenRepository = CookieCsrfTokenRepository.withHttpOnlyFalse()
    csrfTokenRequestHandler = CsrfTokenRequestAttributeHandler()
    // Exempt login endpoint
    ignoringRequestMatchers("/api/auth/login", "/api/auth/register")
}
```
2. **Frontend:** Read CSRF cookie and include in requests:
```typescript
function getCsrfToken(): string | null {
    const match = document.cookie.match(/XSRF-TOKEN=([^;]+)/);
    return match ? match[1] : null;
}

// In fetchApi headers:
const csrfToken = getCsrfToken();
if (csrfToken && ['POST', 'PUT', 'PATCH', 'DELETE'].includes(method)) {
    headers['X-XSRF-TOKEN'] = csrfToken;
}
```

**Note:** If staying with pure JWT Bearer token auth (no cookies), CSRF is less relevant. This item becomes P1 if Chunk 2.3 (httpOnly cookies) is implemented, since cookie-based auth requires CSRF protection.

**Validation:**
- Test: mutating requests without CSRF token return 403.
- Test: requests with valid CSRF token succeed.

---

### 2.5 Input Validation on All Forms

**Gap:** Customer creation form and other forms lack proper validation (length limits, format, sanitization).  
**Files:**
- `numera-ui/src/app/(dashboard)/customers/page.tsx`
- All form components without Zod schemas

**Implementation Steps:**
1. Create Zod schemas for every form:
```typescript
// src/schemas/customer.ts
import { z } from 'zod';
export const customerCreateSchema = z.object({
    customerCode: z.string().min(1).max(50).regex(/^[A-Z0-9-]+$/i, 'Alphanumeric and dashes only'),
    name: z.string().min(1).max(200).trim(),
    industry: z.string().max(100).optional(),
    // ... other fields
});
```
2. Apply `useForm` with `zodResolver` to every form that currently lacks it.
3. Add `maxLength` attributes to all text inputs as defense-in-depth.

**Validation:**
- Test: forms reject empty required fields.
- Test: forms reject excessively long input.
- Test: forms reject special characters where not allowed.

---

### 2.6 Remove Dead Code and Unused Dependencies

**Gap:** `_pages_archive/` folder with ~15 legacy files; `react-router-dom` installed but unused.  
**Files:**
- `numera-ui/src/_pages_archive/` (entire directory)
- `numera-ui/package.json`

**Implementation Steps:**
1. Delete `src/_pages_archive/` directory.
2. Remove `react-router-dom` from `package.json` dependencies.
3. Run `npm install` to clean lock file.
4. Verify no imports reference deleted files.

**Validation:**
- Build succeeds: `npm run build`.
- No broken imports.

---

## CHUNK 3 — Backend Code Quality & Performance

**Priority:** 🟠 P1  
**Scope:** Backend code patterns, performance optimization  
**Dependencies:** None  
**Estimated Files Modified:** 15-20

### 3.1 Consistent Exception Handling

**Gap:** Multiple services use `.orElseThrow()` without messages, producing generic exceptions.  
**Files:**
- `backend/src/main/kotlin/com/numera/spreading/application/SpreadVersionService.kt`
- `backend/src/main/kotlin/com/numera/workflow/application/WorkflowService.kt`
- All services using `.orElseThrow()` or `.get()`

**Implementation Steps:**
1. Create a Kotlin extension function in `shared/exception/`:
```kotlin
// Extensions.kt
fun <T> Optional<T>.orThrow(errorCode: ErrorCode, message: String): T =
    orElseThrow { ApiException(errorCode, message) }

fun <T> Optional<T>.orThrow(errorCode: ErrorCode, lazyMessage: () -> String): T =
    orElseThrow { ApiException(errorCode, lazyMessage()) }
```
2. Find and replace all `.orElseThrow()` calls across the backend with `.orThrow(ErrorCode.NOT_FOUND, "Entity not found: $id")`.
3. Grep for bare `.get()` calls on `Optional` and replace with `.orThrow()`.

**Validation:**
- Compile succeeds.
- Test: calling with nonexistent ID returns proper error message in response body.

---

### 3.2 Read-Only Transaction Optimization

**Gap:** Query-only service methods lack `@Transactional(readOnly = true)`, causing unnecessary write-lock acquisition.  
**Files:**
- `backend/src/main/kotlin/com/numera/customer/application/CustomerService.kt`
- `backend/src/main/kotlin/com/numera/spreading/application/SpreadService.kt`
- `backend/src/main/kotlin/com/numera/covenant/application/CovenantService.kt`
- All service classes with read-only methods

**Implementation Steps:**
1. Audit all service methods. Any method that only reads data and does not call a mutating repository method should have `@Transactional(readOnly = true)`.
2. Pattern to look for: methods named `get*`, `find*`, `list*`, `search*`, `count*`.
3. Add annotation to each qualifying method. Example:
```kotlin
@Transactional(readOnly = true)
fun search(tenantId: UUID, query: String?): List<CustomerResponse> { ... }
```

**Validation:**
- All existing tests pass.
- Enable Hibernate SQL logging and verify no SELECT FOR UPDATE in read paths.

---

### 3.3 Fix N+1 Query Issues

**Gap:** `DocumentProcessingService.kt` line ~232 calls `zoneRepository.findByDocumentId(id!!)` per document in list operations.  
**Files:**
- `backend/src/main/kotlin/com/numera/document/application/DocumentProcessingService.kt`
- `backend/src/main/kotlin/com/numera/document/infrastructure/DocumentRepository.kt`

**Implementation Steps:**
1. Add a `@Query` with JOIN FETCH or use `@EntityGraph`:
```kotlin
@EntityGraph(attributePaths = ["zones"])
fun findByTenantIdAndStatus(tenantId: UUID, status: String): List<Document>
```
2. Alternatively, create a DTO projection query:
```kotlin
@Query("""
    SELECT new com.numera.document.dto.DocumentSummary(
        d.id, d.filename, d.status, COUNT(z.id)
    )
    FROM Document d LEFT JOIN d.zones z
    WHERE d.tenantId = :tenantId
    GROUP BY d.id, d.filename, d.status
""")
fun findSummariesByTenantId(@Param("tenantId") tenantId: UUID): List<DocumentSummary>
```
3. Update `toResponse()` mapping to use the pre-fetched zone count.

**Validation:**
- Enable Hibernate statistics and verify query count for list operations is O(1) not O(n).

---

### 3.4 Graceful Shutdown Configuration

**Gap:** No `server.shutdown` or lifecycle timeout configured.  
**Files:**
- `backend/src/main/resources/application.yml`

**Implementation Steps:**
1. Add to `application.yml`:
```yaml
server:
  shutdown: graceful

spring:
  lifecycle:
    timeout-per-shutdown-phase: 20s
```

**Validation:**
- Send SIGTERM to running app; verify in-flight requests complete before shutdown.

---

### 3.5 Connection Pool Optimization

**Gap:** HikariCP pool not explicitly tuned.  
**Files:**
- `backend/src/main/resources/application.yml`

**Implementation Steps:**
1. Add/tune HikariCP settings:
```yaml
spring:
  datasource:
    hikari:
      maximum-pool-size: ${DB_POOL_MAX:20}
      minimum-idle: ${DB_POOL_MIN:5}
      idle-timeout: 300000
      max-lifetime: 1800000
      connection-timeout: 20000
      leak-detection-threshold: 60000
```
2. Add connection pool metrics to Prometheus by enabling `spring.datasource.hikari.register-mbeans: true`.

**Validation:**
- Observe connection pool metrics in Prometheus/Grafana under load.

---

## CHUNK 4 — API Design & Validation Gaps

**Priority:** 🟠 P1  
**Scope:** REST API improvements  
**Dependencies:** None  
**Estimated Files Modified:** 12-15

### 4.1 Add Pagination to All List Endpoints

**Gap:** Most list endpoints return unbounded results — performance risk at scale.  
**Files (all controllers returning `List<*>`):**
- `backend/src/main/kotlin/com/numera/customer/api/CustomerController.kt`
- `backend/src/main/kotlin/com/numera/spreading/api/SpreadController.kt`
- `backend/src/main/kotlin/com/numera/document/api/DocumentController.kt`
- `backend/src/main/kotlin/com/numera/covenant/api/CovenantController.kt`
- `backend/src/main/kotlin/com/numera/admin/api/UserManagementController.kt`

**Implementation Steps:**
1. Change return type from `List<T>` to `Page<T>` on all list endpoints.
2. Add `Pageable` parameter (Spring auto-resolves from `?page=0&size=20&sort=name,asc`).
3. Set default page size to 20 and max to 100:
```kotlin
@GetMapping
fun list(
    @PageableDefault(size = 20, sort = ["createdAt"], direction = Sort.Direction.DESC)
    pageable: Pageable
): Page<CustomerResponse> {
    return customerService.search(tenantId, pageable).map { it.toResponse() }
}
```
4. Update repository methods from `findAll()` to `findAll(pageable)`.
5. Update corresponding frontend API calls to pass pagination params.

**Validation:**
- Test: default returns 20 items max.
- Test: `?size=101` is capped at 100.
- Test: total count and page info in response.

---

### 4.2 API Response Wrapper Consistency

**Gap:** Inconsistent response structure — some endpoints return bare objects, others return wrapped.  
**Files:**
- `backend/src/main/kotlin/com/numera/shared/dto/ApiResponse.kt` (new)
- All controllers

**Implementation Steps:**
1. Create a standard response wrapper:
```kotlin
data class ApiResponse<T>(
    val data: T,
    val timestamp: Instant = Instant.now(),
    val requestId: String? = null,
)
```
2. Create a `ResponseEntity` factory:
```kotlin
fun <T> ok(data: T): ResponseEntity<ApiResponse<T>> =
    ResponseEntity.ok(ApiResponse(data))

fun <T> created(data: T): ResponseEntity<ApiResponse<T>> =
    ResponseEntity.status(201).body(ApiResponse(data))
```
3. **Phase approach:** Apply to new endpoints first, then migrate existing endpoints one controller at a time to avoid breaking frontend.
4. Document the standard format in OpenAPI.

**Validation:**
- Each endpoint returns `{ "data": {...}, "timestamp": "..." }`.
- Frontend API layer updated to unwrap `response.data`.

---

### 4.3 API Versioning

**Gap:** No API versioning — all at `/api/*`.  
**Files:**
- All controllers' `@RequestMapping`

**Implementation Steps:**
1. Add version prefix: `@RequestMapping("/api/v1/auth")`.
2. For backward compatibility during migration, keep old paths via rewrite in reverse proxy / `WebConfig`:
```kotlin
@Bean
fun legacyApiRewriteFilter() = FilterRegistrationBean(object : OncePerRequestFilter() {
    override fun doFilterInternal(req: HttpServletRequest, res: HttpServletResponse, chain: FilterChain) {
        if (req.requestURI.startsWith("/api/") && !req.requestURI.startsWith("/api/v")) {
            val newUri = req.requestURI.replaceFirst("/api/", "/api/v1/")
            req.getRequestDispatcher(newUri).forward(req, res)
        } else {
            chain.doFilter(req, res)
        }
    }
})
```
3. Update OpenAPI base path.
4. Update frontend API base URL.

**Validation:**
- Both `/api/auth/login` and `/api/v1/auth/login` work.
- OpenAPI shows v1 prefix.

---

### 4.4 Field-Level Validation on All DTOs

**Gap:** Several DTOs lack constraints (`@Size`, `@Digits`, `@Pattern`).  
**Files:**
- `backend/src/main/kotlin/com/numera/spreading/dto/SpreadValueUpdateRequest.kt`
- `backend/src/main/kotlin/com/numera/covenant/dto/` (all DTOs)
- `backend/src/main/kotlin/com/numera/customer/dto/` (all DTOs)
- `backend/src/main/kotlin/com/numera/document/dto/` (all DTOs)

**Implementation Steps:**
1. Audit every DTO `data class` in the codebase.
2. Add constraints following these rules:
   - All `String` fields: `@field:Size(max = X)` with sensible max (names: 200, comments: 2000, codes: 50).
   - All `BigDecimal` fields: `@field:Digits(integer = 18, fraction = 6)`.
   - All enum-like strings: `@field:Pattern(regexp = "^(VALUE1|VALUE2)$")` or convert to actual Kotlin `enum`.
   - All email fields: `@field:Email`.
   - All nullable strings that shouldn't be blank: add custom validation.
3. Ensure `@Valid` is on every `@RequestBody` parameter in controllers.

**Validation:**
- Test: oversized strings return 400.
- Test: invalid patterns return 400.
- Test: valid inputs still work.

---

## CHUNK 5 — Database & Migration Gaps

**Priority:** 🟠 P1  
**Scope:** Database schema, indexes, constraints  
**Dependencies:** None  
**Estimated Files Modified:** 3-5

### 5.1 Add Missing Indexes

**Gap:** Several commonly filtered columns lack indexes.  
**Files:**
- `backend/src/main/resources/db/migration/V036__performance_indexes.sql` (new)

**Implementation Steps:**
1. Create new migration `V036__performance_indexes.sql`:
```sql
-- Customer search optimization
CREATE INDEX IF NOT EXISTS idx_customer_tenant_name
    ON customers(tenant_id, LOWER(name) varchar_pattern_ops);

-- Audit log query optimization
CREATE INDEX IF NOT EXISTS idx_audit_tenant_date
    ON event_log(tenant_id, created_at DESC);

-- Document listing by status
CREATE INDEX IF NOT EXISTS idx_document_tenant_status
    ON documents(tenant_id, status);

-- Spread items by customer
CREATE INDEX IF NOT EXISTS idx_spread_item_customer
    ON spread_items(customer_id, created_at DESC);

-- Covenant monitoring by status
CREATE INDEX IF NOT EXISTS idx_covenant_monitoring_status
    ON covenant_monitoring_items(covenant_id, status);

-- Workflow instances by status
CREATE INDEX IF NOT EXISTS idx_workflow_instance_status
    ON workflow_instances(tenant_id, status);
```

**Validation:**
- Run `EXPLAIN ANALYZE` on common queries before and after.
- All existing tests pass.

---

### 5.2 Add Missing Foreign Key Constraints

**Gap:** Some cross-table references lack FK constraints.  
**Files:**
- `backend/src/main/resources/db/migration/V037__fk_constraints.sql` (new)

**Implementation Steps:**
1. Audit all `VARCHAR` or `UUID` columns that reference other tables but lack `REFERENCES`.
2. Create migration with ALTER TABLE statements to add FKs where appropriate.
3. For any columns with existing orphaned data, clean up first.

**Validation:**
- Migration applies cleanly.
- Orphan insert attempts fail with constraint violation.

---

### 5.3 Add Database Health Check with Pool Monitoring

**Gap:** Basic health endpoint exists but database pool metrics not exposed.  
**Files:**
- `backend/src/main/resources/application.yml`

**Implementation Steps:**
1. Enable detailed health info:
```yaml
management:
  endpoint:
    health:
      show-details: when-authorized
  health:
    db:
      enabled: true
    diskspace:
      enabled: true
    redis:
      enabled: true
    rabbit:
      enabled: true
```

**Validation:**
- `/actuator/health` shows db, redis, rabbit, diskspace components.

---

## CHUNK 6 — Backend Test Coverage

**Priority:** 🔴 P0  
**Scope:** Backend test gaps  
**Dependencies:** Chunks 1, 3 (some tests validate those fixes)  
**Estimated Files Modified:** 8-12 (new test files)

### 6.1 Security Integration Tests

**Files (new):**
- `backend/src/test/kotlin/com/numera/auth/SecurityIntegrationTest.kt`

**Tests to Implement:**
```kotlin
@DisplayName("Security Integration Tests")
class SecurityIntegrationTest : IntegrationTestBase() {
    // JWT Tests
    @Test fun `expired JWT returns 401`()
    @Test fun `malformed JWT returns 401`()
    @Test fun `missing JWT on protected endpoint returns 401`()
    @Test fun `blacklisted JWT returns 401`()  // After Chunk 1.1

    // CORS Tests
    @Test fun `CORS rejects unknown origin`()
    @Test fun `CORS allows configured origin`()

    // Role-Based Access Tests
    @Test fun `ANALYST cannot access admin endpoints`()
    @Test fun `MANAGER can access approval endpoints`()
    @Test fun `ADMIN can access user management`()

    // Rate Limiting Tests (After Chunk 1.5)
    @Test fun `login rate limited after 5 attempts`()

    // Input Validation Tests
    @Test fun `SQL injection in customer search returns empty`()
    @Test fun `XSS in customer name is escaped in response`()
}
```

---

### 6.2 Controller-Level Tests

**Files (new):**
- `backend/src/test/kotlin/com/numera/document/DocumentControllerTest.kt`
- `backend/src/test/kotlin/com/numera/customer/CustomerControllerTest.kt`
- `backend/src/test/kotlin/com/numera/spreading/SpreadControllerTest.kt`

**Tests to Implement:**
- File upload validation (wrong type, too large, missing file).
- Pagination parameters (default, custom, edge cases).
- Path parameter validation (invalid UUID format).
- Request body validation (missing required fields, invalid values).
- Authorization enforcement per endpoint.

---

### 6.3 Database Migration Test

**Files (new):**
- `backend/src/test/kotlin/com/numera/FlywayMigrationTest.kt`

**Tests to Implement:**
```kotlin
@Test fun `all migrations apply cleanly to empty database`()
@Test fun `migrations are idempotent with IF NOT EXISTS`()
@Test fun `all expected tables exist after full migration`()
@Test fun `all expected indexes exist after full migration`()
```

---

### 6.4 Service Edge Case Tests

**Files (new):**
- `backend/src/test/kotlin/com/numera/spreading/SpreadVersionServiceTest.kt`
- `backend/src/test/kotlin/com/numera/workflow/WorkflowServiceTest.kt`

**Tests to Implement:**
- Concurrent spread lock acquisition.
- Version rollback with data consistency.
- Workflow state transition validation (invalid transitions rejected).
- Feature flag toggling behavior.

---

## CHUNK 7 — Frontend Test Infrastructure

**Priority:** 🔴 P0  
**Scope:** Set up frontend testing from scratch  
**Dependencies:** None  
**Estimated Files Modified:** 15-20 (new test files + config)

### 7.1 Test Framework Setup

**Files:**
- `numera-ui/package.json`
- `numera-ui/jest.config.ts` (new)
- `numera-ui/jest.setup.ts` (new)
- `numera-ui/src/__mocks__/` (new)

**Implementation Steps:**
1. Install dependencies:
```bash
npm install -D jest @types/jest @testing-library/react @testing-library/jest-dom @testing-library/user-event jest-environment-jsdom ts-jest
```
2. Create `jest.config.ts`:
```typescript
export default {
    testEnvironment: 'jsdom',
    setupFilesAfterSetup: ['<rootDir>/jest.setup.ts'],
    moduleNameMapper: {
        '^@/(.*)$': '<rootDir>/src/$1',
    },
    transform: {
        '^.+\\.(ts|tsx)$': 'ts-jest',
    },
    collectCoverageFrom: [
        'src/**/*.{ts,tsx}',
        '!src/**/*.d.ts',
        '!src/types/**',
    ],
    coverageThreshold: {
        global: { branches: 50, functions: 50, lines: 50, statements: 50 },
    },
};
```
3. Add test scripts to `package.json`:
```json
"scripts": {
    "test": "jest",
    "test:ci": "jest --ci --coverage",
    "test:watch": "jest --watch"
}
```

---

### 7.2 Critical Component Unit Tests

**Files (new):**
- `numera-ui/src/stores/__tests__/authStore.test.ts`
- `numera-ui/src/services/__tests__/api.test.ts`
- `numera-ui/src/components/ui/__tests__/ErrorBoundary.test.tsx`
- `numera-ui/src/utils/__tests__/sanitize.test.ts` (after Chunk 2.2)

**Tests to Implement:**
```typescript
// authStore.test.ts
describe('authStore', () => {
    it('clears tokens on logout');
    it('refreshes token on 401');
    it('stores user info after login');
    it('redirects to login when refresh fails');
});

// api.test.ts
describe('fetchApi', () => {
    it('includes Authorization header when token exists');
    it('includes X-Tenant-ID header');
    it('retries on 401 with refresh');
    it('throws on 500 errors');
    it('handles network failures');
});

// sanitize.test.ts
describe('sanitizeHtml', () => {
    it('strips script tags');
    it('strips event handlers');
    it('preserves allowed formatting tags');
    it('handles empty input');
});
```

---

### 7.3 E2E Test Setup with Playwright

**Files:**
- `numera-ui/playwright.config.ts` (new)
- `numera-ui/e2e/login.spec.ts` (new)
- `numera-ui/e2e/spreading.spec.ts` (new)

**Implementation Steps:**
1. Install: `npm install -D @playwright/test`.
2. Create config with multiple browsers.
3. Implement critical path tests:
   - Login flow (valid + invalid credentials).
   - Dashboard loads for authenticated user.
   - Spreading workspace basic interaction.

---

## CHUNK 8 — ML Service Hardening

**Priority:** 🟡 P2  
**Scope:** ML service security and quality  
**Dependencies:** None  
**Estimated Files Modified:** 8-10

### 8.1 Add Input Size Limits

**Gap:** No `max_length` constraints on string fields; no `max_items` on list fields.  
**Files:**
- `ml-service/app/api/models.py`

**Implementation Steps:**
1. Add constraints to all Pydantic models:
```python
class ZoneClassificationRequest(BaseModel):
    document_id: str = Field(..., max_length=100)
    tables: list[TableInput] = Field(..., max_length=500)  # Max 500 tables per doc

class MappingSuggestionRequest(BaseModel):
    document_id: str = Field(..., max_length=100)
    source_rows: list[SourceRow] = Field(..., max_length=5000)
    target_items: list[TargetItem] = Field(..., max_length=2000)

class BoundingBox(BaseModel):
    x: float = Field(..., ge=0.0, le=1.0)
    y: float = Field(..., ge=0.0, le=1.0)
    width: float = Field(..., ge=0.0, le=1.0)
    height: float = Field(..., ge=0.0, le=1.0)
```

**Validation:**
- Test: oversized list returns 422.
- Test: invalid bounding box returns 422.

---

### 8.2 Tenant Isolation for Feedback

**Gap:** Any authenticated request can submit feedback for any `tenant_id`.  
**Files:**
- `ml-service/app/api/mapping.py`
- `ml-service/app/api/feedback.py` (if separate)

**Implementation Steps:**
1. Extract `X-Tenant-ID` header in endpoints that accept tenant_id.
2. Validate that the `tenant_id` in the request body matches the `X-Tenant-ID` header:
```python
@router.post("/feedback")
async def submit_feedback(
    request: FeedbackRequest,
    x_tenant_id: str = Header(None, alias="X-Tenant-ID"),
):
    if x_tenant_id and request.tenant_id != x_tenant_id:
        raise HTTPException(403, "Tenant ID mismatch")
```

**Validation:**
- Test: mismatched tenant_id returns 403.

---

### 8.3 Offload CPU-Bound Inference from Event Loop

**Gap:** PyTorch inference blocks asyncio event loop.  
**Files:**
- `ml-service/app/api/zones.py`
- `ml-service/app/api/mapping.py`
- `ml-service/app/api/pipeline.py`

**Implementation Steps:**
1. Use `asyncio.get_event_loop().run_in_executor()` for CPU-bound work:
```python
import asyncio
from concurrent.futures import ThreadPoolExecutor

_inference_pool = ThreadPoolExecutor(max_workers=4, thread_name_prefix="ml-inference")

async def classify_zones(request: ZoneClassificationRequest):
    loop = asyncio.get_event_loop()
    result = await loop.run_in_executor(
        _inference_pool,
        zone_classifier.classify,
        request.tables,
    )
    return result
```

**Validation:**
- Health endpoint remains responsive during long-running inference.
- No asyncio "slow callback" warnings in logs.

---

### 8.4 Add Cache TTL and Invalidation

**Gap:** Embeddings cache, client model cache, fingerprint cache have no TTL.  
**Files:**
- `ml-service/app/ml/embeddings_cache.py`
- `ml-service/app/services/client_model_resolver.py`

**Implementation Steps:**
1. Add `max_age_seconds` to cache entries. When retrieving, check timestamp:
```python
def get_embedding(self, key: str) -> np.ndarray | None:
    entry = self._cache.get(key)
    if entry and (time.time() - entry.timestamp) < self.max_age:
        return entry.vector
    return None
```
2. Add periodic cleanup of expired entries.
3. Add cache invalidation endpoint: `POST /api/ml/admin/cache/clear`.

**Validation:**
- Test: cached entry expires after TTL.
- Test: cache clear endpoint works.

---

### 8.5 Pin Dependency Versions

**Gap:** `hmmlearn>=0.3.0` has no upper bound; risk of breaking changes.  
**Files:**
- `ml-service/requirements.txt`

**Implementation Steps:**
1. Pin all dependencies to minor version ranges:
```
hmmlearn==0.3.3
python-multipart==0.0.17
```
2. Add a `pip-audit` check to CI pipeline.

---

## CHUNK 9 — OCR Service Hardening

**Priority:** 🟡 P2  
**Scope:** OCR service security and reliability  
**Dependencies:** None  
**Estimated Files Modified:** 6-8

### 9.1 Add File Size Limits

**Gap:** No upload size limit — disk exhaustion risk.  
**Files:**
- `ocr-service/app/config.py`
- `ocr-service/app/api/ocr.py`

**Implementation Steps:**
1. Add config: `max_upload_size_mb: int = 200`.
2. Validate file size before processing:
```python
pdf_bytes = storage.download(request.storage_path)
if len(pdf_bytes) > settings.max_upload_size_mb * 1024 * 1024:
    raise HTTPException(413, "File exceeds maximum size limit")
```
3. Add FastAPI `UploadFile` size limit if direct uploads are supported.

**Validation:**
- Test: file over limit returns 413.
- Test: file at limit processes normally.

---

### 9.2 Add Request Timeout Configuration

**Gap:** No timeout for long-running OCR operations.  
**Files:**
- `ocr-service/app/config.py`
- `ocr-service/app/api/ocr.py`

**Implementation Steps:**
1. Add config: `request_timeout_seconds: int = 300`.
2. Wrap processing in asyncio timeout:
```python
import asyncio
try:
    result = await asyncio.wait_for(
        process_document(request),
        timeout=settings.request_timeout_seconds,
    )
except asyncio.TimeoutError:
    raise HTTPException(504, "Document processing timed out")
```

**Validation:**
- Test: processing exceeding timeout returns 504.

---

### 9.3 Restrict CORS Default

**Gap:** Default CORS is `*` (allow all origins).  
**Files:**
- `ocr-service/app/config.py`

**Implementation Steps:**
1. Change default: `cors_origins: str = "http://localhost:3000"`.
2. Validate in production: reject `*`.

---

### 9.4 Offload CPU-Bound VLM Inference

**Gap:** VLM inference blocks asyncio event loop.  
**Files:**
- `ocr-service/app/ml/vlm_processor.py`
- `ocr-service/app/api/ocr.py`

**Implementation Steps:**
1. Same pattern as ML Service (Chunk 8.3): use `run_in_executor` with a `ProcessPoolExecutor`.
2. For VLM inference specifically, consider a dedicated worker process.

---

### 9.5 Add Streaming for Large Files

**Gap:** Entire PDF loaded into memory — risk for large files.  
**Files:**
- `ocr-service/app/services/storage_client.py`
- `ocr-service/app/api/ocr.py`

**Implementation Steps:**
1. Implement page-by-page streaming:
```python
async def process_pages(pdf_bytes: bytes, pages: list[int] | None):
    doc = fitz.open(stream=pdf_bytes, filetype="pdf")
    for page_num in (pages or range(len(doc))):
        page = doc[page_num]
        yield process_single_page(page)
        del page  # Free memory
    doc.close()
```
2. For documents > 50MB, switch to streaming mode automatically.

---

### 9.6 Expand Test Coverage

**Files (new):**
- `ocr-service/tests/test_vlm_pipeline.py`
- `ocr-service/tests/test_storage_errors.py`
- `ocr-service/tests/test_concurrent.py`

**Tests to Implement:**
- VLM inference with mocked model.
- Storage client failure scenarios (MinIO unavailable).
- Concurrent request handling (thread safety).
- PDF with 100+ pages (memory handling).
- Encrypted PDF handling.

---

## CHUNK 10 — Reporting Module Implementation

**Priority:** 🟠 P1  
**Scope:** Backend reporting module (currently empty)  
**Dependencies:** Chunks 3, 5 (indexes for report queries)  
**Estimated Files Modified:** 10-15 (all new)

### 10.1 Create Reporting Domain Model

**Files (new):**
- `backend/src/main/kotlin/com/numera/reporting/domain/Report.kt`
- `backend/src/main/kotlin/com/numera/reporting/domain/ReportDefinition.kt`
- `backend/src/main/kotlin/com/numera/reporting/domain/ReportSchedule.kt`

**Implementation Steps:**
1. Define entities:
```kotlin
@Entity @Table(name = "report_definitions")
data class ReportDefinition(
    @Id @GeneratedValue val id: UUID? = null,
    val tenantId: UUID,
    val name: String,
    @Enumerated(EnumType.STRING) val type: ReportType, // SPREADING_SUMMARY, COVENANT_STATUS, PENDING_APPROVALS, AUDIT_TRAIL
    val config: String, // JSONB: filters, columns, date range
    val createdBy: UUID,
    val isActive: Boolean = true,
)

@Entity @Table(name = "report_schedules")
data class ReportSchedule(
    @Id @GeneratedValue val id: UUID? = null,
    val reportDefinitionId: UUID,
    val cronExpression: String,
    @Enumerated(EnumType.STRING) val deliveryMethod: DeliveryMethod, // EMAIL, DOWNLOAD
    val recipients: String, // JSONB: list of emails
    val isActive: Boolean = true,
)

@Entity @Table(name = "generated_reports")
data class Report(
    @Id @GeneratedValue val id: UUID? = null,
    val tenantId: UUID,
    val reportDefinitionId: UUID,
    @Enumerated(EnumType.STRING) val format: ReportFormat, // PDF, XLSX, CSV
    val storagePath: String,
    val generatedAt: Instant,
    val generatedBy: UUID?,
    val status: String, // PENDING, GENERATING, COMPLETED, FAILED
)
```

---

### 10.2 Create Reporting Service

**Files (new):**
- `backend/src/main/kotlin/com/numera/reporting/application/ReportService.kt`
- `backend/src/main/kotlin/com/numera/reporting/application/ReportGeneratorService.kt`
- `backend/src/main/kotlin/com/numera/reporting/application/ReportSchedulerService.kt`

**Implementation Steps:**
1. `ReportService`: CRUD for report definitions and on-demand generation.
2. `ReportGeneratorService`: Query data from other modules and format into reports:
   - Spreading summary: aggregate spread items by status, customer, period.
   - Covenant status: current covenant states, breaches, upcoming due dates.
   - Pending approvals: workflow items pending action.
   - Audit trail: filtered event log export.
3. `ReportSchedulerService`: `@Scheduled` to check for due report schedules and generate.

---

### 10.3 Create Report Controller

**Files (new):**
- `backend/src/main/kotlin/com/numera/reporting/api/ReportController.kt`

**Endpoints:**
```
GET    /api/v1/reports/definitions          — List report definitions
POST   /api/v1/reports/definitions          — Create report definition
POST   /api/v1/reports/generate             — Generate report on-demand
GET    /api/v1/reports/{reportId}/download   — Download generated report
GET    /api/v1/reports/history               — List generated reports
POST   /api/v1/reports/schedules            — Create scheduled delivery
DELETE /api/v1/reports/schedules/{id}        — Remove schedule
```

---

### 10.4 Create Migration

**Files (new):**
- `backend/src/main/resources/db/migration/V038__reporting.sql`

---

## CHUNK 11 — Infrastructure & DevOps

**Priority:** 🟡 P2  
**Scope:** Docker, Helm, CI/CD improvements  
**Dependencies:** None  
**Estimated Files Modified:** 10-12

### 11.1 Pin Docker Image Tags

**Gap:** `minio/minio:latest` used in docker-compose.  
**Files:**
- `docker-compose.yml`
- `docker-compose.full.yml`

**Implementation Steps:**
1. Replace `latest` tags with specific versions:
```yaml
minio/minio:RELEASE.2024-11-07T00-52-20Z
```

---

### 11.2 Add Multi-Stage Docker Builds

**Gap:** OCR service Dockerfile is single-stage.  
**Files:**
- `ocr-service/Dockerfile`
- `ml-service/Dockerfile`

**Implementation Steps:**
1. Convert to multi-stage:
```dockerfile
FROM python:3.11-slim AS builder
WORKDIR /build
COPY requirements.txt .
RUN pip install --user --no-cache-dir -r requirements.txt

FROM python:3.11-slim
RUN useradd -m -u 1001 app
COPY --from=builder /root/.local /home/app/.local
COPY app/ /app/app/
USER app
ENV PATH="/home/app/.local/bin:$PATH"
```

---

### 11.3 Add Docker Compose Health Check Dependencies

**Gap:** Services don't wait for dependencies to be healthy.  
**Files:**
- `docker-compose.yml`

**Implementation Steps:**
1. Add `depends_on` with health conditions:
```yaml
backend:
  depends_on:
    postgres:
      condition: service_healthy
    redis:
      condition: service_healthy
    rabbitmq:
      condition: service_healthy
```

---

### 11.4 Add Restart Policies

**Gap:** No restart policies in Docker Compose.  
**Files:**
- `docker-compose.yml`
- `docker-compose.full.yml`

**Implementation Steps:**
1. Add `restart: unless-stopped` to all services.

---

### 11.5 Helm: Add OCR Service HPA

**Gap:** OCR service has no auto-scaling.  
**Files:**
- `infra/helm/templates/ocr-service-deployment.yaml`
- `infra/helm/templates/hpa.yaml`

**Implementation Steps:**
1. Add HPA with memory-based scaling (conservative due to VLM model size):
```yaml
- apiVersion: autoscaling/v2
  kind: HorizontalPodAutoscaler
  metadata:
    name: ocr-service-hpa
  spec:
    scaleTargetRef:
      kind: Deployment
      name: ocr-service
    minReplicas: 1
    maxReplicas: 3  # Conservative due to memory
    behavior:
      scaleUp:
        stabilizationWindowSeconds: 300
    metrics:
    - type: Resource
      resource:
        name: cpu
        target:
          type: Utilization
          averageUtilization: 70
```

---

### 11.6 Helm: Enable Network Policy Egress Restrictions

**Gap:** No egress restrictions — services can reach external networks.  
**Files:**
- `infra/helm/templates/network-policy.yaml`

**Implementation Steps:**
1. Add egress rules that only allow:
   - Backend → PostgreSQL, Redis, RabbitMQ, ML Service, OCR Service, MinIO.
   - ML Service → PostgreSQL, MLflow, MinIO.
   - OCR Service → MinIO.
   - Deny all other egress (block internet access from internal services).

---

### 11.7 Add Secret Rotation Support

**Gap:** No secret rotation policy or tooling.  
**Files:**
- `infra/helm/templates/secrets.yaml`

**Implementation Steps:**
1. Document secret rotation procedure.
2. Add external-secrets-operator support:
```yaml
apiVersion: external-secrets.io/v1beta1
kind: ExternalSecret
metadata:
  name: numera-secrets
spec:
  refreshInterval: 1h
  secretStoreRef:
    name: vault-backend
    kind: ClusterSecretStore
  target:
    name: numera-secrets
  data:
    - secretKey: jwt-secret
      remoteRef:
        key: numera/prod/jwt-secret
```

---

## CHUNK 12 — Frontend Functional Gaps

**Priority:** 🟠 P1  
**Scope:** Missing frontend functionality  
**Dependencies:** Chunk 10 (reporting backend)  
**Estimated Files Modified:** 8-12

### 12.1 Reports Page — Rich Rendering

**Gap:** Reports page currently shows raw JSON dumps.  
**Files:**
- `numera-ui/src/app/(dashboard)/reports/page.tsx`

**Implementation Steps:**
1. Replace JSON dump with tabular data display using ag-Grid.
2. Add report type selector (Spreading Summary, Covenant Status, Pending Approvals, Audit Trail).
3. Add date range filter.
4. Add export buttons (PDF, Excel, CSV).
5. Integrate with Chunk 10 report generation API.

---

### 12.2 Lazy Loading for Heavy Components

**Gap:** ag-Grid, Recharts, framer-motion always bundled.  
**Files:**
- Various page components importing heavy libraries

**Implementation Steps:**
1. Use `next/dynamic` for heavy components:
```typescript
const SpreadingGrid = dynamic(
    () => import('@/components/spreading/SpreadingGrid'),
    { loading: () => <LoadingSkeleton />, ssr: false }
);

const CovenantChart = dynamic(
    () => import('@/components/covenant/CovenantChart'),
    { loading: () => <LoadingSkeleton />, ssr: false }
);
```
2. Apply to: admin pages, portfolio analytics, report generation.

---

### 12.3 API Retry Logic for Network Errors

**Gap:** Only retries on 401; 5xx errors have no retry.  
**Files:**
- `numera-ui/src/services/api.ts`

**Implementation Steps:**
1. Add exponential backoff retry for 5xx errors:
```typescript
async function fetchWithRetry(url: string, options: RequestInit, retries = 3): Promise<Response> {
    for (let attempt = 0; attempt < retries; attempt++) {
        const response = await fetch(url, options);
        if (response.status >= 500 && attempt < retries - 1) {
            await new Promise(r => setTimeout(r, Math.pow(2, attempt) * 1000));
            continue;
        }
        return response;
    }
    throw new Error('Max retries exceeded');
}
```

---

### 12.4 Accessibility Improvements

**Gap:** Minimal ARIA support, likely fails WCAG 2.1 Level AA.  
**Files:**
- All component files

**Implementation Steps:**
1. Add skip-to-content link in layout.
2. Add `aria-label` to all icon buttons.
3. Add `aria-live="polite"` regions for dynamic content updates (toast notifications, loading states).
4. Add proper focus management after route changes.
5. Ensure all form inputs have associated labels.
6. Add keyboard navigation for data grids.
7. Ensure color contrast meets WCAG AA (4.5:1 for text).

---

### 12.5 WebSocket Token Refresh

**Gap:** WebSocket uses stale token after access token refresh.  
**Files:**
- `numera-ui/src/services/websocket.ts`

**Implementation Steps:**
1. Listen for token refresh events from authStore.
2. On token refresh, disconnect and reconnect WebSocket with new token:
```typescript
authStore.subscribe((state) => {
    if (state.accessToken !== currentToken) {
        stompClient.deactivate();
        stompClient.connectHeaders = { Authorization: `Bearer ${state.accessToken}` };
        stompClient.activate();
    }
});
```

---

## CHUNK 13 — ML Pipeline Improvements

**Priority:** 🟡 P2  
**Scope:** ML performance and quality  
**Dependencies:** None  
**Estimated Files Modified:** 5-7

### 13.1 Batch Embedding Processing

**Gap:** Semantic matcher processes rows one at a time (N² operations).  
**Files:**
- `ml-service/app/ml/semantic_matcher.py`

**Implementation Steps:**
1. Batch encode all source rows at once:
```python
def suggest_mappings(self, source_rows: list[SourceRow], target_items: list[TargetItem]):
    source_texts = [row.text for row in source_rows]
    target_texts = [item.label for item in target_items]
    
    # Batch encode
    source_embeddings = self.model.encode(source_texts, batch_size=64, show_progress_bar=False)
    target_embeddings = self.model.encode(target_texts, batch_size=64, show_progress_bar=False)
    
    # Vectorized cosine similarity
    similarities = cosine_similarity(source_embeddings, target_embeddings)
    
    # Find best matches
    ...
```

---

### 13.2 Model Signature Validation

**Gap:** No validation that loaded model matches expected input/output schema.  
**Files:**
- `ml-service/app/services/model_manager.py`

**Implementation Steps:**
1. After loading model, validate signature:
```python
def validate_model_signature(model_path: str, expected_input_dims: int):
    # Load model config
    config_path = Path(model_path) / "config.json"
    if config_path.exists():
        config = json.loads(config_path.read_text())
        if config.get("hidden_size") != expected_input_dims:
            raise ValueError(f"Model dimension mismatch: expected {expected_input_dims}")
```

---

### 13.3 Model Cache Expiration

**Gap:** Loaded models never expire from cache; stale models persist.  
**Files:**
- `ml-service/app/services/model_manager.py`

**Implementation Steps:**
1. Add TTL to model cache entries (e.g., 24 hours).
2. Periodically check MLflow for newer versions.
3. Add admin endpoint to force model refresh: `POST /api/ml/admin/models/refresh`.

---

### 13.4 Expand ML Test Coverage

**Files (new):**
- `ml-service/tests/test_semantic_matcher.py`
- `ml-service/tests/test_expression_engine.py`
- `ml-service/tests/test_model_manager.py`
- `ml-service/tests/test_covenant_predictor.py`

**Tests to Implement:**
- Semantic matcher ranking correctness.
- Expression engine operator precedence.
- Model manager download/cache/fallback chain.
- Covenant prediction mathematics (trend regression).
- Database pool initialization and recovery.
- Client model resolver cache eviction at size=20.

---

## CHUNK 14 — Observability & Monitoring

**Priority:** 🟡 P2  
**Scope:** Monitoring, alerting, logging  
**Dependencies:** None  
**Estimated Files Modified:** 5-8

### 14.1 Add Missing Prometheus Alerts

**Gap:** Missing alerts for disk space, queue depth, ML model failures.  
**Files:**
- `infra/prometheus/alerts/numera-alerts.yml`

**Implementation Steps:**
1. Add alerts:
```yaml
- alert: HighDiskUsage
  expr: (node_filesystem_avail_bytes / node_filesystem_size_bytes) * 100 < 15
  for: 10m
  labels:
    severity: warning

- alert: RabbitMQQueueDepth
  expr: rabbitmq_queue_messages > 1000
  for: 5m
  labels:
    severity: warning

- alert: MLModelLoadFailure
  expr: increase(ml_model_load_errors_total[5m]) > 0
  for: 1m
  labels:
    severity: critical

- alert: OCRProcessingBacklog
  expr: rate(ocr_requests_total{status="queued"}[5m]) > rate(ocr_requests_total{status="completed"}[5m])
  for: 10m
  labels:
    severity: warning

- alert: PostgreSQLConnectionPoolExhausted
  expr: hikaricp_connections_active / hikaricp_connections_max > 0.9
  for: 5m
  labels:
    severity: critical

- alert: MinIODiskUsage
  expr: minio_disk_storage_used_bytes / minio_disk_storage_total_bytes > 0.85
  for: 30m
  labels:
    severity: warning
```

---

### 14.2 Add Grafana Dashboards

**Gap:** Dashboards may not cover all services.  
**Files:**
- `infra/grafana/dashboards/` (new or existing)

**Implementation Steps:**
1. Create dashboards:
   - **Backend Overview**: Request rate, error rate, latency P50/P95/P99, JVM heap, GC pauses, active threads.
   - **ML Service**: Model inference latency, A/B test split, feedback volume, cache hit rate.
   - **OCR Service**: Documents processed, processing time by type, VLM vs native ratio, error rate.
   - **Infrastructure**: PostgreSQL connections, Redis memory, RabbitMQ queue depth, MinIO storage.

---

### 14.3 Structured Logging Format

**Gap:** Logs are plain text; harder to aggregate in ELK/Loki.  
**Files:**
- `backend/src/main/resources/application.yml`
- `ml-service/app/main.py`
- `ocr-service/app/main.py`

**Implementation Steps:**
1. Backend: Add `logstash-logback-encoder` for JSON logging:
```gradle
implementation("net.logstash.logback:logstash-logback-encoder:7.4")
```
2. ML/OCR services: Use `python-json-logger`:
```python
import json_logging
json_logging.init_fastapi(enable_json=True)
```
3. Add correlation ID propagation (request ID from header through all log entries).

---

### 14.4 Add Distributed Tracing

**Gap:** OpenTelemetry dependency exists but not configured.  
**Files:**
- `backend/build.gradle.kts`
- `backend/src/main/resources/application.yml`

**Implementation Steps:**
1. Configure OTLP exporter:
```yaml
management:
  tracing:
    sampling:
      probability: 0.1  # 10% in production
  otlp:
    tracing:
      endpoint: ${OTLP_ENDPOINT:http://localhost:4318/v1/traces}
```
2. Propagate trace context in calls to ML/OCR services via headers.

---

## CHUNK 15 — Dependency & Supply Chain Security

**Priority:** 🟡 P2  
**Scope:** Dependency scanning and management  
**Dependencies:** None  
**Estimated Files Modified:** 4-6

### 15.1 Add OWASP Dependency Check (Backend)

**Files:**
- `backend/build.gradle.kts`

**Implementation Steps:**
1. Add plugin:
```kotlin
plugins {
    id("org.owasp.dependencycheck") version "10.0.3"
}

dependencyCheck {
    failBuildOnCVSS = 7.0f
    suppressionFile = "owasp-suppressions.xml"
}
```
2. Create suppression file for known false positives.
3. Add to CI: `./gradlew dependencyCheckAnalyze`.

---

### 15.2 Add npm audit (Frontend)

**Files:**
- `numera-ui/package.json`

**Implementation Steps:**
1. Add script: `"audit": "npm audit --omit=dev --audit-level=high"`.
2. Add to CI pipeline.
3. Consider adding `npm-audit-resolver` for managing exceptions.

---

### 15.3 Add pip-audit (ML & OCR Services)

**Files:**
- `ml-service/requirements.txt`
- `ocr-service/requirements.txt`

**Implementation Steps:**
1. Add `pip-audit` to dev requirements.
2. Add to CI: `pip-audit -r requirements.txt --strict`.
3. Pin all versions to exact (not ranges).

---

### 15.4 Update Outdated PDF Library (Backend)

**Gap:** `openhtmltopdf:1.0.10` is from 2018 with potential CVEs.  
**Files:**
- `backend/build.gradle.kts`

**Implementation Steps:**
1. Evaluate alternatives: `openhtmltopdf:1.1.22` (if available), or `itext:8.x`, or `Apache PDFBox 3.x`.
2. Update dependency and test all PDF generation code paths.
3. Verify no regression in covenant waiver letter PDF generation.

---

### 15.5 Requirements Hash Pinning (Python Services)

**Gap:** No hash pinning in Python requirements files.  
**Files:**
- `ml-service/requirements.txt`
- `ocr-service/requirements.txt`

**Implementation Steps:**
1. Generate pinned requirements with hashes:
```bash
pip-compile --generate-hashes requirements.in -o requirements.txt
```
2. Or migrate to `uv` / `poetry` for lockfile-based dependency management.

---

## Execution Order & Dependencies

```
Priority P0 (Immediate):
  CHUNK 1 → CHUNK 6 (backend security → tests that validate)
  CHUNK 2 → CHUNK 7 (frontend security → tests that validate)

Priority P1 (Week 1-2):
  CHUNK 3 (code quality — independent)
  CHUNK 4 (API design — independent)
  CHUNK 5 (database — independent)
  CHUNK 10 → CHUNK 12 (reporting backend → frontend)

Priority P2 (Week 3-4):
  CHUNK 8 (ML hardening — independent)
  CHUNK 9 (OCR hardening — independent)
  CHUNK 11 (infra — independent)
  CHUNK 13 (ML pipeline — independent)
  CHUNK 14 (observability — independent)
  CHUNK 15 (dependency security — independent)
```

**Independence Matrix** (✅ = can run in parallel):

| | C1 | C2 | C3 | C4 | C5 | C6 | C7 | C8 | C9 | C10 | C11 | C12 | C13 | C14 | C15 |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| **C1** | — | ✅ | ✅ | ✅ | ✅ | ⬆️ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| **C2** | ✅ | — | ✅ | ✅ | ✅ | ✅ | ⬆️ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| **C10** | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | — | ✅ | ⬆️ | ✅ | ✅ | ✅ |

⬆️ = depends on (must complete first)

---

## Summary Statistics

| Metric | Value |
|--------|-------|
| **Total gaps identified** | 70 |
| **Critical (P0)** | 19 |
| **High (P1)** | 30 |
| **Medium (P2)** | 21 |
| **New files to create** | ~45 |
| **Existing files to modify** | ~60 |
| **Independent chunks** | 15 |
| **Fully parallel chunks** | 12 (of 15) |
