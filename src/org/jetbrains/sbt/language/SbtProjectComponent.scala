package org.jetbrains.sbt
package language

import com.intellij.openapi.components.AbstractProjectComponent
import com.intellij.openapi.project.Project
import com.intellij.psi.{PsiTreeChangeEvent, PsiTreeChangeAdapter, PsiManager}
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer

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
}
