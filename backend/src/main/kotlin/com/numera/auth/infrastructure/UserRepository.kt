package com.numera.auth.infrastructure

import com.numera.auth.domain.User
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.Optional
import java.util.UUID

@Repository
interface UserRepository : JpaRepository<User, UUID> {
    fun findByEmailIgnoreCase(email: String): Optional<User>
}