package com.numera.workflow.domain

import com.numera.shared.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table

@Entity
@Table(name = "workflow_step_definitions")
class WorkflowStepDefinition : BaseEntity() {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "workflow_definition_id", nullable = false)
    var workflowDefinition: WorkflowDefinition? = null

    @Column(nullable = false)
    var stepOrder: Int = 0

    @Column(nullable = false)
    var name: String = ""

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var type: StepType = StepType.START

    var requiredRole: String? = null

    var slaHours: Int? = null

    var escalateTo: String? = null

    @Column(nullable = false)
    var autoApproveOnSlaExpiry: Boolean = false

    var conditionExpression: String? = null
}
