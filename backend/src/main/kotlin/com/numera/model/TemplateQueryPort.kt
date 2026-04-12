package com.numera.model

import com.numera.model.domain.ModelTemplate
import com.numera.model.infrastructure.TemplateRepository
import com.numera.shared.exception.ApiException
import com.numera.shared.exception.ErrorCode
import org.springframework.stereotype.Service
import java.util.UUID

/**
 * Public API for template look-up exposed by the model module.
 *
 * Lives in the model ROOT package so other modules (e.g. spreading) can resolve ModelTemplate
 * entities without crossing into model's private infrastructure package.
 */
@Service
class TemplateQueryPort(
    private val templateRepository: TemplateRepository,
) {
    /** Loads the template entity by ID, throwing NOT_FOUND if absent. */
    fun findEntityById(id: UUID): ModelTemplate =
        templateRepository.findById(id)
            .orElseThrow { ApiException(ErrorCode.NOT_FOUND, "Template not found") }
}
