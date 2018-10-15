package org.jetbrains.plugins.scala.codeInsight.implicits

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.openapi.editor.{Editor, EditorFactory, InlayModel}
import com.intellij.openapi.project.ProjectManager
import com.intellij.psi.PsiFile
import org.jetbrains.plugins.scala.extensions.ObjectExt

private object ImplicitHints {
  private val ModificationCount = new ModificationCount("IMPLICIT_HINTS_MODIFICATION_COUNT")

  val ExpansionThreshold = 5

  private var _enabled: Boolean = false
  private var _expanded: Boolean = false

  def enabled: Boolean = _enabled

  def enabled_=(b: Boolean): Unit = {
    _enabled = b
    _expanded = false
    removeAllShortcuts(ExpandImplicitHintsAction.Id)
    setShortcuts(ShowImplicitHintsAction.Id, if (b) DisableShortcuts else EnableShortcuts)
    if (b) {
      setShortcuts(ExpandImplicitHintsAction.Id, EnableShortcuts)
    }
  }

  def expanded: Boolean = _expanded

  def expanded_=(b: Boolean): Unit = {
    _expanded = b
    removeAllShortcuts(ShowImplicitHintsAction.Id)
    setShortcuts(ExpandImplicitHintsAction.Id, if (b) DisableShortcuts else EnableShortcuts)
    if (!b) {
      setShortcuts(ShowImplicitHintsAction.Id, DisableShortcuts)
    }
  }

  def isUpToDate(editor: Editor, file: PsiFile): Boolean =
    ModificationCount(editor) == ModificationCount(file)

  def setUpToDate(editor: Editor, file: PsiFile): Unit = {
    ModificationCount(editor) = ModificationCount(file)
  }

  def updateInAllEditors(): Unit = {
    EditorFactory.getInstance().getAllEditors.foreach(ModificationCount(_) = 0L)

    ProjectManager.getInstance().getOpenProjects
      .foreach(project => DaemonCodeAnalyzer.getInstance(project).restart())
  }

  def expandIn(editor: Editor): Unit = {
    expand(editor, ExpansionThreshold)
  }

  def collapseIn(editor: Editor): Unit = {
    expand(editor, 0)
  }

  private def expand(editor: Editor, level: Int): Unit = {
    val model = editor.getInlayModel

    model.getInlineElementsInRange(0, Int.MaxValue).forEach { inlay =>
      inlay.getRenderer.asOptionOf[PresentationRenderer].foreach(_.presentation.expand(level))
    }
  }
}


