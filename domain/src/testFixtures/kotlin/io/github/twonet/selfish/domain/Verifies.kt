package io.github.twonet.selfish.domain

/**
 * Declares which SRS requirements a test verifies, for example
 * `@Verifies("FR-SRCH-02")` (NFR-MNT-02, DESIGN §8.2).
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
annotation class Verifies(vararg val ids: String)
