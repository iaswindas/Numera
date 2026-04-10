package com.numera.model.infrastructure

import com.numera.model.domain.ModelValidation
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface ValidationRepository : JpaRepository<ModelValidation, UUID> {
    fun findByTemplateId(templateId: UUID): List<ModelValidation>
}