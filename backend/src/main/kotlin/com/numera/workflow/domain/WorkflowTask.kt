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
import java.time.Instant

@Entity
@Table(name = "workflow_tasks")
class WorkflowTask : BaseEntity() {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "workflow_instance_id", nullable = false)
    var instance: WorkflowInstance? = null

    @Column(nullable = false)
    var stepOrder: Int = 0

    @Column(nullable = false)
    var stepName: String = ""

    var assignee: String? = null

    var assignedRole: String? = null

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: TaskStatus = TaskStatus.PENDING

    var outcome: String? = null

    var comment: String? = null

    var dueAt: Instant? = null

    var completedAt: Instant? = null

    var completedBy: String? = null

    var escalatedAt: Instant? = null
}
