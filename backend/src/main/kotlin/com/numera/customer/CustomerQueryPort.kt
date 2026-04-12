package com.numera.customer

import com.numera.customer.domain.Customer
import com.numera.customer.infrastructure.CustomerRepository
import com.numera.shared.exception.ApiException
import com.numera.shared.exception.ErrorCode
import org.springframework.stereotype.Service
import java.util.UUID

/**
 * Public API for customer look-up exposed by the customer module.
 *
 * Lives in the customer ROOT package so that the spreading, document, and other modules can look
 * up Customer entities without crossing into customer's private infrastructure package.
 *
 * The [Customer] type returned here is accessible via the customer.domain @NamedInterface.
 */
@Service
class CustomerQueryPort(
    private val customerRepository: CustomerRepository,
) {
    /** Loads the Customer entity by ID, throwing NOT_FOUND if absent. */
    fun findEntityById(id: UUID): Customer =
        customerRepository.findById(id)
            .orElseThrow { ApiException(ErrorCode.NOT_FOUND, "Customer not found") }

    /** Returns the total number of customers in the store. */
    fun count(): Long = customerRepository.count()
}
