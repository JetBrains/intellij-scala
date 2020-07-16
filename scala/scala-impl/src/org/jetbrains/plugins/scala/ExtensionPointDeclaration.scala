package org.jetbrains.plugins.scala

import com.intellij.openapi.extensions.ExtensionPointName

/**
  * Handy base class for declaring extension points.
  */
abstract class ExtensionPointDeclaration[T](private val name: String) {
  private val extensionPointName = ExtensionPointName.create[T](name)

  def implementations: Seq[T] = {
    import scala.jdk.CollectionConverters.asScalaBufferConverter
    extensionPointName.getExtensionList.asScala
  }
}
