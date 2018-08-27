package org.jetbrains.plugins.scala.decompileToJava

final case class DecompilationError(message: String, cause: Option[Throwable] = None)
  extends RuntimeException(message, cause.orNull)
