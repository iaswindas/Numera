package com.numera.covenant.api

import com.numera.covenant.application.CovenantService
import com.numera.covenant.dto.CovenantCustomerRequest
import com.numera.covenant.dto.CovenantCustomerResponse
import com.numera.covenant.dto.CovenantRequest
import com.numera.covenant.dto.CovenantResponse
import jakarta.validation.Valid
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.data.web.PageableDefault
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/covenants")
class CovenantController(
    private val covenantService: CovenantService,
) {

    // ── Covenant Customers ────────────────────────────────────────────────

    @GetMapping("/customers")
    fun listCustomers(
        @RequestParam(required = false) query: String?,
        @PageableDefault(size = 20, sort = ["createdAt"], direction = Sort.Direction.DESC)
        pageable: Pageable,
    ): Page<CovenantCustomerResponse> = covenantService.listCovenantCustomers(query, pageable)

    @GetMapping("/customers/{id}")
    fun getCustomer(@PathVariable id: UUID): CovenantCustomerResponse =
        covenantService.getCovenantCustomer(id)

    @PostMapping("/customers")
    @ResponseStatus(HttpStatus.CREATED)
    fun createCustomer(@Valid @RequestBody request: CovenantCustomerRequest): CovenantCustomerResponse =
        covenantService.createCovenantCustomer(request)

    @PutMapping("/customers/{id}")
    fun updateCustomer(
        @PathVariable id: UUID,
        @Valid @RequestBody request: CovenantCustomerRequest,
    ): CovenantCustomerResponse = covenantService.updateCovenantCustomer(id, request)

    @PatchMapping("/customers/{id}/active")
    fun toggleCustomerActive(
        @PathVariable id: UUID,
        @RequestParam active: Boolean,
    ): CovenantCustomerResponse = covenantService.toggleActive(id, active)

    // ── Covenant Definitions ──────────────────────────────────────────────

    @GetMapping("/definitions")
    fun listCovenants(
        @RequestParam covenantCustomerId: UUID,
        @PageableDefault(size = 20, sort = ["createdAt"], direction = Sort.Direction.DESC)
        pageable: Pageable,
    ): Page<CovenantResponse> = covenantService.listCovenants(covenantCustomerId, pageable)

    @GetMapping("/definitions/{id}")
    fun getCovenant(@PathVariable id: UUID): CovenantResponse =
        covenantService.getCovenant(id)

    @PostMapping("/definitions")
    @ResponseStatus(HttpStatus.CREATED)
    fun createCovenant(@Valid @RequestBody request: CovenantRequest): CovenantResponse =
        covenantService.createCovenant(request)

    @PutMapping("/definitions/{id}")
    fun updateCovenant(
        @PathVariable id: UUID,
        @Valid @RequestBody request: CovenantRequest,
    ): CovenantResponse = covenantService.updateCovenant(id, request)

    @DeleteMapping("/definitions/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deactivateCovenant(@PathVariable id: UUID) = covenantService.deactivateCovenant(id)
}
