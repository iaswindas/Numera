package com.numera.document

import com.numera.document.infrastructure.MinioStorageClient
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile

/**
 * Public API for file-storage operations exposed by the document module.
 *
 * Lives in the document ROOT package so that modules like covenant can store documents
 * (e.g. waiver attachments) without crossing into document's private infrastructure package.
 */
@Service
class StoragePort(
    private val minioStorageClient: MinioStorageClient,
) {
    /**
     * Uploads a file to object storage and returns the storage path (bucket/objectName).
     * The path can later be used with download operations via the document module.
     */
    fun upload(file: MultipartFile): String = minioStorageClient.upload(file)
}
