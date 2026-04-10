package com.numera.customer.api

import com.numera.customer.application.CustomerService
import com.numera.customer.dto.CustomerRequest
import com.numera.customer.dto.CustomerResponse
import com.numera.customer.dto.CustomerSearchRequest
import com.numera.shared.domain.TenantAwareEntity
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
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
@RequestMapping("/api/customers")
class CustomerController(
    private val customerService: CustomerService,
) {
    private val tenantId = TenantAwareEntity.DEFAULT_TENANT

    @GetMapping
    fun list(
        @RequestParam(required = false) query: String?,
        @RequestParam(required = false) industry: String?,
        @RequestParam(required = false) country: String?,
    ): List<CustomerResponse> = customerService.search(tenantId, CustomerSearchRequest(query, industry, country))

    @GetMapping("/{id}")
    fun get(@PathVariable id: UUID): CustomerResponse = customerService.findById(id)

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(@Valid @RequestBody request: CustomerRequest): CustomerResponse = customerService.create(tenantId, request)

    @PutMapping("/{id}")
    fun update(@PathVariable id: UUID, @Valid @RequestBody request: CustomerRequest): CustomerResponse =
        customerService.update(id, request)

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(@PathVariable id: UUID) = customerService.delete(id)
}