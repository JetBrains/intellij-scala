package org.jetbrains.plugins.scala.codeInsight.implicits

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.openapi.editor.{Editor, EditorFactory}
import com.intellij.psi.PsiFile

private object ImplicitHints {
  private val ModificationCount = new ModificationCount("IMPLICIT_HINTS_MODIFICATION_COUNT")

  private var _enabled: Boolean = false

  def enabled: Boolean = _enabled

  def enabled_=(b: Boolean): Unit = {
    _enabled = b
    updateInAllEditors()
  }

  def isUpToDate(editor: Editor, file: PsiFile): Boolean =
    ModificationCount(editor) == ModificationCount(file)

  def setUpToDate(editor: Editor, file: PsiFile): Unit = {
    ModificationCount(editor) = ModificationCount(file)
  }

  private def updateInAllEditors(): Unit = {
    EditorFactory.getInstance().getAllEditors.foreach(ModificationCount(_) = 0L)

    EditorFactory.getInstance().getAllEditors.map(_.getProject).distinct
      .foreach(project => DaemonCodeAnalyzer.getInstance(project).restart())
  }
}


