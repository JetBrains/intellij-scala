package org.jetbrains.plugins.scala
package incremental

import settings.ScalaProjectSettings

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement

// SCL-23216
object Highlighting {
  private[incremental] var editor: Editor = _

  private[incremental] var suppress: Boolean = false

  def enabledIn(project: Project): Boolean =
    !suppress && project != null && ScalaProjectSettings.in(project).isIncrementalHighlighting

  def update(enabled: Boolean, project: Project): Unit = {
    if (enabled) {
      Listener.connectTo(project)
    } else {
      Listener.disconnectFrom(project)
    }
    DaemonCodeAnalyzer.getInstance(project).restart()
  }

  implicit class ElementHighlightingExt(private val e: PsiElement) extends AnyVal {
    def isVisible: Boolean =
      !enabledIn(e.getProject) || VisibleRange.isVisible(e)
  }
}
