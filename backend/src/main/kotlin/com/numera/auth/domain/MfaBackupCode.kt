package com.numera.auth.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "mfa_backup_codes")
class MfaBackupCode {
    @Id
    var id: UUID = UUID.randomUUID()

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    var user: User = User()

    @Column(name = "code_hash", nullable = false)
    var codeHash: String = ""

    @Column(nullable = false)
    var used: Boolean = false

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now()
}
