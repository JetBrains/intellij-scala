package org.jetbrains.sbt
package language

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.openapi.components.AbstractProjectComponent
import com.intellij.openapi.project.Project
import com.intellij.psi.{PsiManager, PsiTreeChangeAdapter, PsiTreeChangeEvent}
import org.jetbrains.idea.maven.indices.MavenProjectIndicesManager
import org.jetbrains.idea.maven.services.MavenRepositoryService

/**
 * @author Pavel Fatin
 */
class SbtProjectComponent(project: Project) extends AbstractProjectComponent(project) {
  override def initComponent() {
    manager.addPsiTreeChangeListener(TreeListener)
  }

  override def disposeComponent() {
    manager.removePsiTreeChangeListener(TreeListener)
  }

  private def manager = PsiManager.getInstance(project)

  private def analyzer = DaemonCodeAnalyzer.getInstance(project)

  object TreeListener extends PsiTreeChangeAdapter {
    override def childrenChanged(event: PsiTreeChangeEvent) {
      event.getFile match {
        case file: SbtFileImpl => analyzer.restart(file)
        case _ =>
      }
    }
  }

  override def projectOpened(): Unit = {
    MavenProjectIndicesManager.getInstance(project).scheduleUpdateIndicesList(null)
  }
}
