package org.jetbrains.plugins.scala
package components.libextensions

import com.intellij.openapi.project.Project

import scala.reflect.ClassTag

private[scala]
final class DynamicExtensionPoint[T](implicit val tag: ClassTag[T]) {

  def getExtensions(implicit project: Project): Seq[T] =
    LibraryExtensionsManager
      .getInstance(project)
      .getExtensions(tag.runtimeClass)
      .toSeq
      .asInstanceOf[Seq[T]]
}
