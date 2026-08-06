package org.jetbrains.plugins.scala.project.maven

import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.project.Project
import org.jetbrains.idea.maven.buildtool.quickfix.MavenFullSyncQuickFix
import org.jetbrains.plugins.scala.compiler.sync.SyncAction

private object MavenSyncAction extends SyncAction {
  override def syncProject(project: Project, dataContext: DataContext): Unit = {
    val quickFix = new MavenFullSyncQuickFix()
    quickFix.runQuickFix(project, dataContext)
  }
}
