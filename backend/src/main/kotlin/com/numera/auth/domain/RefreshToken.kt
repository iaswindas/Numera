package com.numera.auth.domain

import com.numera.shared.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(name = "refresh_tokens")
class RefreshToken : BaseEntity() {
    @ManyToOne(optional = false)
    @JoinColumn(name = "user_id")
    lateinit var user: User

    @Column(nullable = false, length = 800)
    var token: String = ""

    @Column(nullable = false)
    var expiresAt: Instant = Instant.now()

    @Column(nullable = false)
    var revoked: Boolean = false
}