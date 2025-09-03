package com.notivest.pricefetcher.security

/**
 * Annotation para requerir scopes específicos en métodos de controller
 *
 * Uso:
 * @RequireScope("read:prices")
 * @RequireScope("write:prices", "admin:all")  // Requiere cualquiera de los dos
 */
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class RequireScope(vararg val value: String)

/**
 * Annotation para requerir TODOS los scopes especificados
 *
 * Uso:
 * @RequireAllScopes("read:prices", "service:internal")  // Requiere ambos
 */
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class RequireAllScopes(vararg val value: String)

/**
 * Annotation para requerir que el token sea de tipo servicio
 */
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class RequireServiceToken
