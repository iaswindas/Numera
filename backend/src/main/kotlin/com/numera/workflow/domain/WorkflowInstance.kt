package com.numera.workflow.domain

import com.numera.shared.domain.TenantAwareEntity
import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.OneToMany
import jakarta.persistence.OrderBy
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "workflow_instances")
class WorkflowInstance : TenantAwareEntity() {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "workflow_definition_id", nullable = false)
    var definition: WorkflowDefinition? = null

    @Column(nullable = false)
    var entityType: String = ""

    @Column(nullable = false)
    var entityId: UUID = UUID.randomUUID()

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: InstanceStatus = InstanceStatus.ACTIVE

    @Column(nullable = false)
    var currentStepOrder: Int = 0

    @Column(nullable = false)
    var startedAt: Instant = Instant.now()

    var completedAt: Instant? = null

    @Column(nullable = false)
    var startedBy: String = ""

    @OneToMany(mappedBy = "instance", cascade = [CascadeType.ALL], orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("stepOrder ASC")
    var tasks: MutableList<WorkflowTask> = mutableListOf()
}
