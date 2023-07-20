package org.jetbrains.plugins.scala.components

import com.intellij.openapi.vfs.JarFileSystem

import java.io.File
import scala.collection.mutable.ArrayBuffer


package object libextensions {
  class ExtensionException(message: String) extends Exception(message)
  class ExtensionAlreadyLoadedException(file: File) extends ExtensionException(s"Extensions jar $file is already loaded")
  class NoManifestInExtensionJarException(file: File) extends ExtensionException(s"Extensions jar $file has no manifest")
  class BadManifestException(file: File, cause: Throwable) extends ExtensionException(s"Failed to parse extension manifest from jar $file:\n$cause")
  class BadExtensionDescriptor(file: File, error: String) extends ExtensionException(s"Failed to extract extensions descriptors from xml in file $file: $error")

  case class ExtensionJarData(descriptor: LibraryDescriptor, file: File, loadedExtensions: Map[Class[_], ArrayBuffer[Any]])
  case class ExtensionProps(artifact: String, urlOverride: String)

  final implicit class JarPathStringExt(private val path: String) extends AnyVal {
    def withJarSeparator: String =
      if (path.contains(JarFileSystem.JAR_SEPARATOR)) path
      else path + JarFileSystem.JAR_SEPARATOR
  }
}
