package org.jetbrains.plugins.scala

import com.intellij.openapi.extensions.ExtensionPointName

/**
  * Handy base class for declaring extension points.
  */
abstract class ExtensionPointDeclaration[T](private val name: String) {
  private val extensionPointName: ExtensionPointName[T] = ExtensionPointName.create(name)

  def implementations: Seq[T] = extensionPointName.getExtensions
}
