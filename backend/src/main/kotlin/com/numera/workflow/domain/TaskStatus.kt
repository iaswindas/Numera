package com.numera.workflow.domain

enum class TaskStatus {
    PENDING,
    IN_PROGRESS,
    COMPLETED,
    SKIPPED,
    ESCALATED,
    TIMED_OUT,
}
