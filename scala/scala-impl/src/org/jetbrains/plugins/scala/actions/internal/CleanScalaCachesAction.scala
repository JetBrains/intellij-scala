package org.jetbrains.plugins.scala.actions.internal

import com.intellij.openapi.actionSystem.{ActionUpdateThread, AnAction, AnActionEvent}
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiManager

//noinspection ScalaExtractStringToBundle,ScalaUnusedSymbol,ComponentNotRegistered
final class CleanScalaCachesAction extends AnAction("Clean Scala Plugin Caches") {

  override def actionPerformed(event: AnActionEvent): Unit = {
    val project = event.getProject
    if (project != null) {
      CleanScalaCachesAction.cleanAllCaches(project)
    }
  }

  override def getActionUpdateThread: ActionUpdateThread = ActionUpdateThread.BGT
}

object CleanScalaCachesAction {

  def cleanAllCaches(project: Project): Unit =
    ScalaPsiManager.instance(project).clearAllCachesAndWait()
}
