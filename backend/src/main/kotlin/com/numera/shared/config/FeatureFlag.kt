package com.numera.shared.config

/**
 * Documents that the annotated element is gated behind a feature flag.
 */
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.RUNTIME)
annotation class FeatureFlag(val name: String)
