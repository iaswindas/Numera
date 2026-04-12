package com.numera.shared.pdf

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.io.ByteArrayOutputStream

@Service
class PdfGenerator {
    private val log = LoggerFactory.getLogger(javaClass)

    fun renderHtml(htmlContent: String, title: String? = null): ByteArray {
        val normalizedHtml = normalizeHtml(htmlContent, title)
        val output = ByteArrayOutputStream()

        runCatching {
            PdfRendererBuilder()
                .useFastMode()
                .withHtmlContent(normalizedHtml, null)
                .toStream(output)
                .run()
        }.onFailure { ex ->
            log.error("Failed to render PDF from HTML", ex)
            throw IllegalStateException("Unable to generate PDF", ex)
        }

        return output.toByteArray()
    }

    private fun normalizeHtml(htmlContent: String, title: String?): String {
        val trimmed = htmlContent.trim()
        if (trimmed.contains("<html", ignoreCase = true)) {
            return trimmed
        }

        val safeTitle = escapeHtml(title ?: "Document")
        return """
            <!DOCTYPE html>
            <html>
              <head>
                <meta charset="UTF-8" />
                <title>$safeTitle</title>
                <style>
                  body {
                    font-family: Helvetica, Arial, sans-serif;
                    font-size: 11pt;
                    line-height: 1.45;
                    color: #111827;
                    margin: 24px;
                  }
                  p { margin: 0 0 12px 0; }
                  strong { font-weight: 700; }
                </style>
              </head>
              <body>
                $trimmed
              </body>
            </html>
        """.trimIndent()
    }

    private fun escapeHtml(value: String): String = value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
}