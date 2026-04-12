/**
 * Named interface exposing the Customer JPA entity so that the spreading module (SpreadItem,
 * ExpressionPattern), the document module (Document), and the covenant module (CovenantCustomer)
 * can hold @ManyToOne references to Customer without crossing module private boundaries.
 */
@org.springframework.modulith.NamedInterface("domain")
package com.numera.customer.domain;
