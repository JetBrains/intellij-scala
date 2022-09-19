package org.jetbrains.sbt
package language

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.psi.{PsiManager, PsiTreeChangeAdapter, PsiTreeChangeEvent}
import org.jetbrains.idea.maven.indices.MavenIndicesManager

import scala.annotation.nowarn

final class SbtProjectService(project: Project) extends Disposable {

  setupMavenIndexes()

  private def manager = PsiManager.getInstance(project)

  manager.addPsiTreeChangeListener(TreeListener): @nowarn("cat=deprecation")

  private def analyzer = DaemonCodeAnalyzer.getInstance(project)

  override def dispose(): Unit = {
    manager.removePsiTreeChangeListener(TreeListener)
  }

  object TreeListener extends PsiTreeChangeAdapter {
    override def childrenChanged(event: PsiTreeChangeEvent): Unit = {
      event.getFile match {
        case file: SbtFileImpl => analyzer.restart(file)
        case _ =>
      }
    }
  }

  private def setupMavenIndexes(): Unit =
    if (!ApplicationManager.getApplication.isUnitTestMode && isIdeaPluginEnabled("org.jetbrains.idea.maven"))
      MavenIndicesManager.getInstance(project).scheduleUpdateIndicesList(null)

}
