package com.numera.shared.security

object TenantContext {
    private val currentTenant = ThreadLocal<String>()

    fun set(tenantId: String) = currentTenant.set(tenantId)

    fun get(): String? = currentTenant.get()

    fun clear() = currentTenant.remove()
}