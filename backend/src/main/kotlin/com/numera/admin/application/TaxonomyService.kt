package com.numera.admin.application

import com.numera.admin.domain.TaxonomyEntry
import com.numera.admin.infrastructure.TaxonomyEntryRepository
import com.numera.shared.domain.TenantAwareEntity
import com.numera.shared.security.TenantContext
import com.numera.shared.exception.ApiException
import com.numera.shared.exception.ErrorCode
import org.apache.poi.ss.usermodel.WorkbookFactory
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.io.InputStream
import java.util.UUID

@Service
class TaxonomyService(
    private val repository: TaxonomyEntryRepository,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private fun resolvedTenantId(): java.util.UUID =
        TenantContext.get()?.let { java.util.UUID.fromString(it) } ?: TenantAwareEntity.DEFAULT_TENANT

    fun list(language: String? = null): List<Map<String, Any?>> {
        val entries = if (language != null) {
            repository.findByTenantIdAndLanguage(resolvedTenantId(), language)
        } else {
            repository.findByTenantId(resolvedTenantId())
        }
        return entries.map { it.toMap() }
    }

    fun getByCategory(category: String): List<Map<String, Any?>> =
        repository.findByTenantIdAndCategory(resolvedTenantId(), category).map { it.toMap() }

    @Transactional
    fun upsert(itemCode: String, label: String, category: String?, parentCode: String?,
               synonyms: List<String>?, language: String): Map<String, Any?> {
        val existing = repository.findByTenantIdAndItemCodeAndLanguage(resolvedTenantId(), itemCode, language)
        val entry = existing.orElse(TaxonomyEntry().also {
            it.tenantId = resolvedTenantId()
            it.itemCode = itemCode
            it.language = language
        })
        entry.label = label
        entry.category = category
        entry.parentCode = parentCode
        entry.synonyms = synonyms?.toTypedArray() ?: entry.synonyms
        return repository.save(entry).toMap()
    }

    @Transactional
    fun delete(id: UUID) {
        repository.deleteById(id)
    }

    /**
     * Bulk import from Excel file.
     * Expected columns: item_code | label | category | parent_code | synonyms (comma-separated) | language
     */
    @Transactional
    fun bulkImport(inputStream: InputStream): Map<String, Any> {
        val workbook = WorkbookFactory.create(inputStream)
        val sheet = workbook.getSheetAt(0)
        var imported = 0
        var updated = 0
        var errors = 0

        for (rowIndex in 1..sheet.lastRowNum) { // Skip header row
            val row = sheet.getRow(rowIndex) ?: continue
            try {
                val itemCode = row.getCell(0)?.stringCellValue?.trim() ?: continue
                val label = row.getCell(1)?.stringCellValue?.trim() ?: continue
                val category = row.getCell(2)?.stringCellValue?.trim()
                val parentCode = row.getCell(3)?.stringCellValue?.trim()
                val synonymsStr = row.getCell(4)?.stringCellValue?.trim()
                val language = row.getCell(5)?.stringCellValue?.trim() ?: "en"

                val synonyms = synonymsStr?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() }

                val existing = repository.findByTenantIdAndItemCodeAndLanguage(resolvedTenantId(), itemCode, language)
                val entry = existing.orElse(TaxonomyEntry().also {
                    it.tenantId = resolvedTenantId()
                    it.itemCode = itemCode
                    it.language = language
                })

                val isNew = !existing.isPresent
                entry.label = label
                entry.category = category
                entry.parentCode = parentCode
                entry.synonyms = synonyms?.toTypedArray() ?: entry.synonyms
                repository.save(entry)

                if (isNew) imported++ else updated++
            } catch (e: Exception) {
                log.warn("Error importing row {}: {}", rowIndex, e.message)
                errors++
            }
        }

        workbook.close()
        return mapOf("imported" to imported, "updated" to updated, "errors" to errors)
    }

    /**
     * Export taxonomy entries ready for Excel download.
     */
    fun exportData(): List<Map<String, Any?>> =
        repository.findByTenantId(resolvedTenantId()).map {
            mapOf(
                "itemCode" to it.itemCode,
                "label" to it.label,
                "category" to it.category,
                "parentCode" to it.parentCode,
                "synonyms" to it.synonyms.joinToString(", "),
                "language" to it.language,
            )
        }

    private fun TaxonomyEntry.toMap() = mapOf(
        "id" to id.toString(),
        "itemCode" to itemCode,
        "label" to label,
        "category" to category,
        "parentCode" to parentCode,
        "synonyms" to synonyms.toList(),
        "language" to language,
        "isActive" to isActive,
    )
}
