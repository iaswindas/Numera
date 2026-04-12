/**
 * Named interface exposing the Document JPA entity so that the spreading module (SpreadItem)
 * and the spreading application layer (MappingOrchestrator) can reference Document without
 * crossing module private boundaries.
 */
@org.springframework.modulith.NamedInterface("domain")
package com.numera.document.domain;
