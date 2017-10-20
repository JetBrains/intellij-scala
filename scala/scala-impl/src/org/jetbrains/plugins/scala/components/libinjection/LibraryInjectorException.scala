package org.jetbrains.plugins.scala.components.libinjection

import java.io.File

/**
  * Created by mucianm on 18.03.16.
  */
trait LibraryInjectorException
trait InjectorCompileException extends LibraryInjectorException
trait InjectorCacheException extends LibraryInjectorException
trait InjectorIOException extends LibraryInjectorException

object Error {
  def compilationError(cause: String): Nothing = {
    throw new Exception(cause) with InjectorCompileException
  }

  def cacheSaveError(cause: Throwable): Nothing = {
    throw new Exception("Failed to save injector cache", cause) with InjectorCacheException
  }

  def noJarFound(path: File): Nothing = {
    throw new Exception(s"Failed to locate source jar file - $path") with InjectorIOException
  }

  def extractFailed(injectorName: String, outDir: File): Nothing = {
    throw new Exception(s"Failed to extract injector sources for $injectorName - failed to create directory $outDir")
      with InjectorIOException
  }
}
