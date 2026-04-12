package com.numera.document.infrastructure

import com.numera.shared.config.NumeraProperties
import io.minio.BucketExistsArgs
import io.minio.GetObjectArgs
import io.minio.MakeBucketArgs
import io.minio.MinioClient
import io.minio.PutObjectArgs
import org.springframework.stereotype.Component
import org.springframework.web.multipart.MultipartFile
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.util.UUID

@Component
class MinioStorageClient(
    private val config: NumeraProperties,
) {
    private val client: MinioClient = MinioClient.builder()
        .endpoint(config.storage.endpoint)
        .credentials(config.storage.accessKey, config.storage.secretKey)
        .build()

    fun upload(file: MultipartFile): String {
        val bucket = config.storage.bucket
        ensureBucket(bucket)

        val safeName = sanitizeFilename(file.originalFilename ?: "document")
        val objectName = "${UUID.randomUUID()}-$safeName"
        client.putObject(
            PutObjectArgs.builder()
                .bucket(bucket)
                .`object`(objectName)
                .stream(ByteArrayInputStream(file.bytes), file.size, -1)
                .contentType(file.contentType ?: "application/octet-stream")
                .build()
        )
        return "$bucket/$objectName"
    }

    /**
     * Sanitize a user-provided filename to prevent path-traversal and injection attacks.
     * Strips directory separators, null bytes, and shell metacharacters.
     */
    private fun sanitizeFilename(name: String): String {
        // Take only the filename portion (strip any directory path)
        val basename = name.substringAfterLast('/').substringAfterLast('\\')
        // Remove null bytes, control characters, and shell metacharacters
        val cleaned = basename.replace(Regex("[\\x00-\\x1f\\x7f;|&`\$!#]"), "")
            .replace("..", "_")  // Prevent directory traversal
            .trim()
        return cleaned.ifBlank { "document" }
    }

    fun download(storagePath: String): InputStream {
        val parts = storagePath.split("/", limit = 2)
        val bucket = parts[0]
        val objectName = parts[1]
        return client.getObject(
            GetObjectArgs.builder()
                .bucket(bucket)
                .`object`(objectName)
                .build()
        )
    }

    private fun ensureBucket(bucket: String) {
        val exists = client.bucketExists(BucketExistsArgs.builder().bucket(bucket).build())
        if (!exists) {
            client.makeBucket(MakeBucketArgs.builder().bucket(bucket).build())
        }
    }
}