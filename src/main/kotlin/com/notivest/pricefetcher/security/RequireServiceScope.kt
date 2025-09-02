package com.notivest.pricefetcher.security

/**
 * Annotation para requerir scopes específicos en métodos de controller para calls de servicio
 * 
 * Uso:
 * @RequireServiceScope("read:prices")
 * @RequireServiceScope("write:prices", "admin:all")  // Requiere cualquiera de los dos
 */
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class RequireServiceScope(vararg val value: String)

/**
 * Annotation para endpoints que pueden ser llamados tanto por usuarios como por servicios
 */
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class AllowBothCallTypes(
  val userScopes: Array<String> = [],
  val serviceScopes: Array<String> = []
)
