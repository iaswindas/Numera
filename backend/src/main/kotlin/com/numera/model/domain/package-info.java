/**
 * Named interface exposing key model domain types (ModelTemplate, ModelLineItem, ModelItemType)
 * so that the spreading module's JPA entities (SpreadItem, ExpressionPattern) can hold @ManyToOne
 * references to ModelTemplate without crossing module private boundaries.
 */
@org.springframework.modulith.NamedInterface("domain")
package com.numera.model.domain;
