package org.jetbrains.plugins.scala.compiler.sync

import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.project.Project

trait SyncAction {
  def syncProject(project: Project, dataContext: DataContext): Unit
}
