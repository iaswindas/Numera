package com.numera.document

import com.numera.document.application.DocumentProcessingService
import com.numera.document.domain.DocumentStatus
import com.numera.document.infrastructure.MinioStorageClient
import com.numera.support.IntegrationTestBase
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.BDDMockito.given
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.utility.MountableFile

class MlPipelineIntegrationTest : IntegrationTestBase() {

    @Autowired
    private lateinit var documentProcessingService: DocumentProcessingService

    @MockBean
    private lateinit var minioStorageClient: MinioStorageClient

    @Test
    fun `process document end to end with containerized ocr and ml services`() {
        val user = createUser()
        val customer = createCustomer()
        val document = createDocument(customer, user).also {
            it.status = DocumentStatus.UPLOADED
            it.storagePath = "numera-docs/doc-e2e.pdf"
            it.language = "en"
            documentRepository.save(it)
        }

        given(minioStorageClient.upload(any())).willReturn("numera-docs/doc-e2e.pdf")

        val result = documentProcessingService.process(document.id!!)
        val persisted = documentRepository.findById(document.id!!).orElseThrow()

        assertEquals(DocumentStatus.READY, result.status)
        assertEquals(DocumentStatus.READY, persisted.status)
        assertEquals("native", persisted.backendUsed)
        assertEquals("native", persisted.pdfType)
        assertTrue((persisted.processingTimeMs ?: 0) > 0)

        val zones = documentProcessingService.zones(document.id!!).zones
        assertEquals(1, zones.size)
        assertEquals("INCOME_STATEMENT", zones.first().zoneType)
        assertEquals("VLM", zones.first().classificationMethod)
    }

    companion object {
        @Container
        @JvmStatic
        val ocrMock: GenericContainer<*> = GenericContainer("wiremock/wiremock:3.5.4")
            .withExposedPorts(8080)
            .withCopyFileToContainer(
                MountableFile.forClasspathResource("wiremock/ocr"),
                "/home/wiremock",
            )
            .waitingFor(Wait.forHttp("/__admin").forStatusCode(200))

        @Container
        @JvmStatic
        val mlMock: GenericContainer<*> = GenericContainer("wiremock/wiremock:3.5.4")
            .withExposedPorts(8080)
            .withCopyFileToContainer(
                MountableFile.forClasspathResource("wiremock/ml"),
                "/home/wiremock",
            )
            .waitingFor(Wait.forHttp("/__admin").forStatusCode(200))

        @JvmStatic
        @DynamicPropertySource
        fun registerMlServiceUrls(registry: DynamicPropertyRegistry) {
            registry.add("numera.ml.ocr-service-url") { "http://${ocrMock.host}:${ocrMock.getMappedPort(8080)}/api" }
            registry.add("numera.ml.ml-service-url") { "http://${mlMock.host}:${mlMock.getMappedPort(8080)}/api" }
            registry.add("numera.ml.timeout-ms") { 5000 }
            registry.add("numera.ml.retry-max-attempts") { 2 }
            registry.add("numera.ml.retry-backoff-ms") { 100 }
            registry.add("numera.ml.circuit-breaker-failure-threshold") { 2 }
            registry.add("numera.ml.circuit-breaker-open-ms") { 1000 }
        }
    }
}
