package com.numera.workflow.domain

import com.numera.shared.domain.TenantAwareEntity
import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.OneToMany
import jakarta.persistence.OrderBy
import jakarta.persistence.Table

@Entity
@Table(name = "workflow_definitions")
class WorkflowDefinition : TenantAwareEntity() {

    @Column(nullable = false)
    var name: String = ""

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var type: WorkflowType = WorkflowType.CUSTOM

    @Column(nullable = false)
    var version: Int = 1

    @Column(nullable = false)
    var active: Boolean = true

    var description: String? = null

    @OneToMany(mappedBy = "workflowDefinition", cascade = [CascadeType.ALL], orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("stepOrder ASC")
    var steps: MutableList<WorkflowStepDefinition> = mutableListOf()
}
