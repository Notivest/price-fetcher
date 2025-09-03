package com.notivest.pricefetcher.security

import org.slf4j.Logger

/**
 * Extension function para simplificar el logging de información del caller
 */
fun Logger.logCaller(
  action: String,
  vararg additionalInfo: Any?,
) {
  val callContext = UnifiedContext.getCurrentContext()
  val callerType = if (UnifiedContext.isUserCall()) "user" else "service"
  val identifier = callContext?.getIdentifier() ?: "unknown"

  val message =
    "$action requested by {}: {}" +
      if (additionalInfo.isNotEmpty()) " " + additionalInfo.joinToString(" ") { "{}" } else ""

  val allArgs = mutableListOf<Any?>(callerType, identifier)
  allArgs.addAll(additionalInfo)
  this.info(message, *allArgs.toTypedArray())
}
