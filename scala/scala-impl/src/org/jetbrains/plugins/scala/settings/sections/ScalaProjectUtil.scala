package org.jetbrains.plugins.scala.settings.sections

import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.project.ProjectExt

private object ScalaProjectUtil {
  def hasScala2(project: Project): Boolean = project.hasScala2

  def hasScala3(project: Project): Boolean = project.hasScala3
}
