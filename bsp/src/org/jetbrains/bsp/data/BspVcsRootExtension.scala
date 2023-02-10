package org.jetbrains.bsp.data

import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.ExtensionPointDeclaration

trait BspVcsRootExtension {
  def onVcsRootAdded(project: Project): Unit
}

object BspVcsRootExtension extends ExtensionPointDeclaration[BspVcsRootExtension]("com.intellij.bspVcsRootExtension") {
  def onVcsRootAdded(project: Project): Unit = {
    implementations.foreach(_.onVcsRootAdded(project))
  }
}
