package com.numera.support

import com.numera.auth.domain.Role
import com.numera.auth.domain.User
import com.numera.auth.infrastructure.RoleRepository
import com.numera.auth.infrastructure.UserRepository
import com.numera.customer.domain.Customer
import com.numera.customer.infrastructure.CustomerRepository
import com.numera.document.domain.Document
import com.numera.document.domain.DocumentStatus
import com.numera.document.infrastructure.DocumentRepository
import com.numera.model.domain.ModelItemType
import com.numera.model.domain.ModelLineItem
import com.numera.model.domain.ModelTemplate
import com.numera.model.domain.ModelValidation
import com.numera.model.infrastructure.LineItemRepository
import com.numera.model.infrastructure.TemplateRepository
import com.numera.model.infrastructure.ValidationRepository
import com.numera.shared.domain.TenantAwareEntity
import com.numera.shared.security.JwtTokenProvider
import org.junit.jupiter.api.BeforeEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.LocalDate
import java.time.Instant

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
abstract class IntegrationTestBase {
    @Autowired protected lateinit var jdbcTemplate: JdbcTemplate
    @Autowired protected lateinit var passwordEncoder: PasswordEncoder
    @Autowired protected lateinit var userRepository: UserRepository
    @Autowired protected lateinit var roleRepository: RoleRepository
    @Autowired protected lateinit var customerRepository: CustomerRepository
    @Autowired protected lateinit var documentRepository: DocumentRepository
    @Autowired protected lateinit var templateRepository: TemplateRepository
    @Autowired protected lateinit var lineItemRepository: LineItemRepository
    @Autowired protected lateinit var validationRepository: ValidationRepository
    @Autowired protected lateinit var jwtTokenProvider: JwtTokenProvider

    @BeforeEach
    fun resetDatabase() {
        jdbcTemplate.execute(
            """
            TRUNCATE TABLE refresh_tokens, user_roles, role_permissions, spread_versions, spread_values,
            expression_patterns, spread_items, detected_zones, documents, model_validations,
            model_line_items, model_templates, event_log, customers, users, roles, password_policies RESTART IDENTITY CASCADE
            """
        )
        jdbcTemplate.update(
            """
            INSERT INTO tenants (id, code, name, active, created_at, updated_at)
            VALUES (?, ?, ?, true, now(), now())
            ON CONFLICT (id) DO UPDATE SET code = EXCLUDED.code, name = EXCLUDED.name, active = true
            """.trimIndent(),
            TenantAwareEntity.DEFAULT_TENANT,
            "demo",
            "Demo Tenant",
        )
    }

    protected fun createUser(
        email: String = "analyst@numera.ai",
        password: String = "Password123!",
        roleName: String = "ROLE_ANALYST",
        fullName: String = "Demo Analyst",
    ): User {
        val role = roleRepository.save(Role().also {
            it.tenantId = TenantAwareEntity.DEFAULT_TENANT
            it.name = roleName
        })

        return userRepository.save(User().also {
            it.tenantId = TenantAwareEntity.DEFAULT_TENANT
            it.email = email
            it.passwordHash = passwordEncoder.encode(password)
            it.passwordChangedAt = Instant.now()
            it.passwordHistory = "[]"
            it.fullName = fullName
            it.enabled = true
            it.roles.add(role)
        })
    }

    protected fun bearerFor(user: User): String =
        "Bearer " + jwtTokenProvider.generateAccessToken(
            user.email,
            user.tenantId.toString(),
            user.roles.map { it.name.removePrefix("ROLE_") },
        )

    protected fun createCustomer(name: String = "Acme Corp", code: String = "ACME01"): Customer =
        customerRepository.save(Customer().also {
            it.tenantId = TenantAwareEntity.DEFAULT_TENANT
            it.name = name
            it.customerCode = code
            it.industry = "Manufacturing"
            it.country = "AE"
            it.relationshipManager = "RM One"
        })

    protected fun createDocument(customer: Customer, uploadedBy: User): Document =
        documentRepository.save(Document().also {
            it.tenantId = TenantAwareEntity.DEFAULT_TENANT
            it.customer = customer
            it.fileName = "statement.pdf"
            it.originalFilename = "statement.pdf"
            it.storagePath = "numera-docs/statement.pdf"
            it.fileSize = 1024
            it.contentType = "application/pdf"
            it.language = "en"
            it.status = DocumentStatus.READY
            it.uploadedBy = uploadedBy.id.toString()
            it.uploadedByName = uploadedBy.fullName
            it.pdfType = "NATIVE"
            it.backendUsed = "native"
            it.totalPages = 3
            it.processingTimeMs = 1200
        })

    protected fun createTemplate(name: String = "IFRS Corporate"): ModelTemplate =
        templateRepository.save(ModelTemplate().also {
            it.tenantId = TenantAwareEntity.DEFAULT_TENANT
            it.name = name
            it.version = 1
            it.currency = "AED"
            it.active = true
        })

    protected fun addLineItem(
        template: ModelTemplate,
        itemCode: String,
        label: String,
        itemType: ModelItemType = ModelItemType.INPUT,
        formula: String? = null,
        required: Boolean = false,
        total: Boolean = false,
        sortOrder: Int,
        zone: String = "INCOME_STATEMENT",
    ): ModelLineItem = lineItemRepository.save(ModelLineItem().also {
        it.template = template
        it.itemCode = itemCode
        it.label = label
        it.zone = zone
        it.category = "Operating Revenue"
        it.itemType = itemType
        it.formula = formula
        it.required = required
        it.total = total
        it.indentLevel = 0
        it.signConvention = "NATURAL"
        it.aliasesJson = "[\"$label\"]"
        it.sortOrder = sortOrder
    })

    protected fun addValidation(template: ModelTemplate, name: String, expression: String, severity: String = "ERROR"): ModelValidation =
        validationRepository.save(ModelValidation().also {
            it.template = template
            it.name = name
            it.expression = expression
            it.severity = severity
        })

    protected fun today(): String = LocalDate.now().toString()

    companion object {
        @Container
        @JvmStatic
        val postgres: PostgreSQLContainer<*> = PostgreSQLContainer("postgres:16-alpine")
            .withDatabaseName("numera_test")
            .withUsername("numera")
            .withPassword("numera")

        @JvmStatic
        @DynamicPropertySource
        fun registerProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url", postgres::getJdbcUrl)
            registry.add("spring.datasource.username", postgres::getUsername)
            registry.add("spring.datasource.password", postgres::getPassword)
            registry.add("spring.datasource.driver-class-name") { "org.postgresql.Driver" }
            registry.add("spring.flyway.url", postgres::getJdbcUrl)
            registry.add("spring.flyway.user", postgres::getUsername)
            registry.add("spring.flyway.password", postgres::getPassword)
        }
    }
}
