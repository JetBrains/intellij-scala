package org.jetbrains.plugins.scala.util

import org.jetbrains.plugins.scala.project.ProjectContext
import org.jetbrains.plugins.scala.settings.ScalaProjectSettings

object AliasExports {
  def aliasExportsEnabled(implicit context: ProjectContext): Boolean =
    ScalaProjectSettings.in(context.project).aliasExportsEnabled

  def stringClass(implicit context: ProjectContext): String =
    if (aliasExportsEnabled) "java.lang.String"
    else "scala.Predef.String"

  def exceptionClass(implicit context: ProjectContext): String =
    if (aliasExportsEnabled) "java.lang.Exception"
    else "scala.Exception"
}
