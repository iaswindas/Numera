package com.numera.workflow.domain

enum class StepType {
    START,
    APPROVAL,
    CONDITION,
    PARALLEL_GATEWAY,
    TIMER,
    ESCALATION,
    END,
}
