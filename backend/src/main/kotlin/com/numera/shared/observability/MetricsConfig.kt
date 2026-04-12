package com.numera.shared.observability

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import org.springframework.context.annotation.Configuration
import jakarta.annotation.PostConstruct
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

@Configuration
class MetricsConfig(private val registry: MeterRegistry) {

    // ── Gauges (current state) ───────────────────────────────────
    val documentQueueDepth = AtomicInteger(0)
    val mlQueueDepth = AtomicInteger(0)
    val activeSpreadLocks = AtomicInteger(0)
    val activeSessions = AtomicInteger(0)

    // ── Counters ─────────────────────────────────────────────────
    lateinit var documentsProcessed: Counter
    lateinit var covenantChecks: Counter
    lateinit var covenantBreaches: Counter
    lateinit var gdprExportRequests: Counter
    lateinit var gdprDeletionRequests: Counter

    // ── Timers (histograms) ──────────────────────────────────────
    lateinit var apiLatencyTimer: Timer
    lateinit var mlInferenceTimer: Timer
    lateinit var ocrProcessingTimer: Timer
    lateinit var spreadingSessionTimer: Timer
    lateinit var documentUploadTimer: Timer

    @PostConstruct
    fun registerMetrics() {
        // Gauges
        Gauge.builder("numera_document_queue_depth") { documentQueueDepth.get() }
            .description("Number of documents awaiting processing")
            .register(registry)

        Gauge.builder("numera_ml_queue_depth") { mlQueueDepth.get() }
            .description("Number of ML inference requests queued")
            .register(registry)

        Gauge.builder("numera_active_spread_locks") { activeSpreadLocks.get() }
            .description("Number of active pessimistic spread locks")
            .register(registry)

        Gauge.builder("numera_active_sessions") { activeSessions.get() }
            .description("Number of active user sessions")
            .register(registry)

        // Counters
        documentsProcessed = Counter.builder("numera_documents_processed_total")
            .description("Total documents processed")
            .register(registry)

        covenantChecks = Counter.builder("numera_covenant_checks_total")
            .description("Total covenant compliance checks")
            .register(registry)

        covenantBreaches = Counter.builder("numera_covenant_breaches_total")
            .description("Total covenant breach detections")
            .register(registry)

        gdprExportRequests = Counter.builder("numera_gdpr_export_requests_total")
            .description("Total GDPR data export requests")
            .register(registry)

        gdprDeletionRequests = Counter.builder("numera_gdpr_deletion_requests_total")
            .description("Total GDPR data deletion requests")
            .register(registry)

        // Timers
        apiLatencyTimer = Timer.builder("numera_api_latency_seconds")
            .description("API request latency")
            .publishPercentiles(0.5, 0.95, 0.99)
            .register(registry)

        mlInferenceTimer = Timer.builder("numera_ml_inference_duration_seconds")
            .description("ML model inference duration")
            .publishPercentiles(0.5, 0.95, 0.99)
            .tag("model", "default")
            .register(registry)

        ocrProcessingTimer = Timer.builder("numera_ocr_processing_duration_seconds")
            .description("OCR document processing duration")
            .publishPercentiles(0.5, 0.95, 0.99)
            .register(registry)

        spreadingSessionTimer = Timer.builder("numera_spreading_session_duration_seconds")
            .description("End-to-end spreading session duration")
            .publishPercentiles(0.5, 0.95, 0.99)
            .register(registry)

        documentUploadTimer = Timer.builder("numera_document_upload_duration_seconds")
            .description("Document upload processing duration")
            .publishPercentiles(0.5, 0.95, 0.99)
            .register(registry)
    }

    // ── Helpers for tagged timers ────────────────────────────────

    fun mlInferenceTimerForModel(model: String): Timer =
        Timer.builder("numera_ml_inference_duration_seconds")
            .description("ML model inference duration")
            .publishPercentiles(0.5, 0.95, 0.99)
            .tag("model", model)
            .register(registry)

    fun covenantBreachCounter(severity: String): Counter =
        Counter.builder("numera_covenant_breaches_total")
            .description("Total covenant breach detections")
            .tag("severity", severity)
            .register(registry)
}
