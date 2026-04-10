package com.numera.model.domain

import com.numera.shared.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table

@Entity
@Table(name = "model_validations")
class ModelValidation : BaseEntity() {
    @ManyToOne(optional = false)
    @JoinColumn(name = "template_id")
    lateinit var template: ModelTemplate

    @Column(nullable = false)
    var name: String = ""

    @Column(nullable = false)
    var expression: String = ""

    @Column(nullable = false)
    var severity: String = "WARNING"
}