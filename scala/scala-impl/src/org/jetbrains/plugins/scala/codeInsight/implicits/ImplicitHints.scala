package org.jetbrains.plugins.scala.codeInsight.implicits

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.openapi.editor.{Editor, EditorFactory, InlayModel}
import com.intellij.openapi.project.ProjectManager
import com.intellij.psi.PsiFile
import org.jetbrains.plugins.scala.extensions.ObjectExt

private object ImplicitHints {
  private val ModificationCount = new ModificationCount("IMPLICIT_HINTS_MODIFICATION_COUNT")

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
    val model = editor.getInlayModel

    inlaysIn(model).forEach { inlay =>
      inlay.getRenderer.asOptionOf[TextRenderer].foreach(_.expand())
      inlay.updateSize()
    }
  }

  def collapseIn(editor: Editor): Unit = {
    val model = editor.getInlayModel

    inlaysIn(model).forEach { inlay =>
      inlay.getRenderer.asOptionOf[TextRenderer].filter(_.expanded).foreach(_.collapse())
      inlay.updateSize()
    }
  }

  private def inlaysIn(model: InlayModel) =
    model.getInlineElementsInRange(0, Int.MaxValue)
}


