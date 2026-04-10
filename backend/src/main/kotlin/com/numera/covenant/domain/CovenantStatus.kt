package com.numera.covenant.domain

enum class CovenantStatus {
    // Financial covenant statuses
    DUE,
    OVERDUE,
    MET,
    BREACHED,
    TRIGGER_ACTION,
    CLOSED,
    // Non-financial covenant statuses (also reuses DUE, OVERDUE, BREACHED, CLOSED)
    SUBMITTED,
    APPROVED,
    REJECTED,
}
