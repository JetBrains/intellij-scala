package org.jetbrains.plugins.scala
package incremental

import settings.ScalaProjectSettings
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.jetbrains.annotations.Nullable
import org.jetbrains.plugins.scala.project.ProjectExt

// SCL-23216
object Highlighting {
  private[incremental] var editor: Editor = _

  private[incremental] var suppress: Boolean = false

  def builtInHighlightingDisabledIn(project: Project): Boolean = {
    val settings = ScalaProjectSettings.in(project)
    def isCompilerHighlightingOnly = (settings.isCompilerHighlightingScala2 || !project.hasScala2) && (settings.isCompilerHighlightingScala3 || !project.hasScala3)
    settings.isDisableInspections && isCompilerHighlightingOnly
  }

  def enabledIn(@Nullable project: Project): Boolean =
    !suppress && project != null && ScalaProjectSettings.in(project).isIncrementalHighlighting

  def update(enabled: Boolean, project: Project): Unit = {
    if (enabled) {
      Listener.connectTo(project)
    } else {
      Listener.disconnectFrom(project)
    }
    DaemonCodeAnalyzer.getInstance(project).restart("Restart after updating Highlighting settings")
  }

  implicit class ElementHighlightingExt(private val e: PsiElement) extends AnyVal {
    def isVisible: Boolean = {
      if (builtInHighlightingDisabledIn(e.getProject)) return false

      !enabledIn(e.getProject) || VisibleRange.isVisible(e)
    }
  }
}
