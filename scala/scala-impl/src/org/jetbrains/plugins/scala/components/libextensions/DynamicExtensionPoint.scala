package org.jetbrains.plugins.scala.components.libextensions

import com.intellij.openapi.project.Project

import scala.reflect.ClassTag

class DynamicExtensionPoint[T](implicit val tag: ClassTag[T]) {

  def getExtensions(implicit project: Project): Seq[T] = {
    LibraryExtensionsManager.getInstance(project).getExtensions(tag.runtimeClass).asInstanceOf[Seq[T]]
  }

  def getExtension(implicit project: Project): T = getExtensions.head
}
