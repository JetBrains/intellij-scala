package org.jetbrains.scalaCli.actions

import com.intellij.openapi.actionSystem.{CommonDataKeys, DataContext}
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.actions.NewScalaFileActionExtension
import org.jetbrains.scalaCli.ScalaCliUtils

class ScalaCliNewScalaFileActionExtension extends NewScalaFileActionExtension {

  // Scala CLI doesn't have a concept of a source directory
  // BSP server reports individual files as source roots (even though it's a file)
  // So, we allow creating scala classes/files in any directory in Scala CLI projects
  override def isAvailable(dataContext: DataContext): Boolean = {
    val project = dataContext.getData(CommonDataKeys.PROJECT)
    project != null && isScalaCliProject(project)
  }

  private def isScalaCliProject(project: Project): Boolean = {
    //for now, we don't have a dedicated Scala CLI project system id and BSP is used under the hood
    ScalaCliUtils.isBspScalaCliProject(project)
  }
}
