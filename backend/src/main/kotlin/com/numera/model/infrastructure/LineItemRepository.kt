package com.numera.model.infrastructure

import com.numera.model.domain.ModelLineItem
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface LineItemRepository : JpaRepository<ModelLineItem, UUID> {
    fun findByTemplateIdOrderBySortOrderAsc(templateId: UUID): List<ModelLineItem>
    fun findByTemplateIdAndZoneOrderBySortOrderAsc(templateId: UUID, zone: String): List<ModelLineItem>
}
