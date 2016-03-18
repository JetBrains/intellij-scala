package org.jetbrains.plugins.scala.components.libinjection

/**
  * Created by mucianm on 18.03.16.
  */
trait LibraryInjectorException
trait InjectorCompileException extends LibraryInjectorException

object Error {
  def compilationError(cause: String) = throw new Exception(cause) with InjectorCompileException
}
