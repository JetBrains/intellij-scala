package org.jetbrains.plugins.scala
package incremental

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.{PsiElement, PsiFile}
import org.jetbrains.annotations.Nullable
import org.jetbrains.plugins.scala.settings.ScalaHighlightingMode

// SCL-23216
object Highlighting {
  // All Scala editors (including in Diff Viewer). Accessed from multiple threads. Faster than EditorFactory.getEditors.
  // Effectively mirrors Listener.updaters.keySet but as Seq and with tread safety.
  @volatile
  private[incremental] var editors = Seq.empty[Editor]

  private[incremental] var editor: Editor = scala.compiletime.uninitialized

  private[incremental] def suppress: Boolean = ScalaHighlightingMode.incrementalHighlightingSuppressed

  private[incremental] def suppress_=(value: Boolean): Unit = ScalaHighlightingMode.incrementalHighlightingSuppressed = value

  def builtInHighlightingDisabledIn(project: Project): Boolean = ScalaHighlightingMode.isBuiltInHighlightingDisabled(project)

  def enabledIn(@Nullable project: Project): Boolean = ScalaHighlightingMode.isIncrementalHighlightingEnabled(project)

  def update(enabled: Boolean, project: Project): Unit = {
    if (enabled) {
      Listener.connectTo(project)
    } else {
      Listener.disconnectFrom(project)
    }
    DaemonCodeAnalyzer.getInstance(project).restart("Restart after updating Highlighting settings")
  }

  implicit class ElementHighlightingExt(private val e: PsiElement) extends AnyVal {
    // The parameters are for optimization: e.getProject and e.getContainingFile require tree walk-up and checkCanceled, see SCL-25054
    def isVisible(project: Project, @Nullable file: PsiFile): Boolean = {
      if (builtInHighlightingDisabledIn(project)) return false

      !enabledIn(project) || (file != null && VisibleRange.isVisible(file, e.getTextRange))
    }
  }
}
