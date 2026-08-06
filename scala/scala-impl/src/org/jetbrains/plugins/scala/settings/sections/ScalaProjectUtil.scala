package org.jetbrains.plugins.scala.settings.sections

import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.project.{ModuleExt, ProjectExt, ScalaLanguageLevel}

private object ScalaProjectUtil {
  def hasScala2(project: Project): Boolean = project.hasScala2

  def hasScala3(project: Project): Boolean = project.hasScala3

  def hasScala(project: Project, level: ScalaLanguageLevel): Boolean =
    project.modulesWithScala.exists(_.scalaLanguageLevel.exists(_ >= level))
}
