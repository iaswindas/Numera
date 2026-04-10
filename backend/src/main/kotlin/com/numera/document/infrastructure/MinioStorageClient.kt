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

        val objectName = "${UUID.randomUUID()}-${file.originalFilename}"
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