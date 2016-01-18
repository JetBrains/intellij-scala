package org.jetbrains.plugins.hocon

import com.intellij.openapi.actionSystem._
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.actionSystem.EditorActionManager
import com.intellij.openapi.fileEditor.{FileEditorManager, OpenFileDescriptor}
import com.intellij.psi.PsiFile
import com.intellij.testFramework.TestActionEvent

/**
  * @author ghik
  */
abstract class HoconActionTest(actionId: String, subpath: String)
  extends HoconFileSetTestCase("actions/" + subpath) {

  // Code based on AbstractEnterActionTestBase

  private class MockDataContext(file: PsiFile, editor: Editor) extends DataContext with DataProvider {
    def getData(dataId: String): AnyRef =
      if (LangDataKeys.LANGUAGE is dataId) file.getLanguage
      else if (CommonDataKeys.PROJECT is dataId) file.getProject
      else if (CommonDataKeys.EDITOR is dataId) editor
      else null
  }

  protected def transform(data: Seq[String]): String = {
    val (fileText, offset) = extractCaret(data.head)
    val psiFile = HoconTestUtils.createPseudoPhysicalHoconFile(getProject, fileText)

    val editorManager = FileEditorManager.getInstance(getProject)
    val editor = editorManager.openTextEditor(new OpenFileDescriptor(getProject, psiFile.getVirtualFile, 0), false)
    assert(editor != null)
    editor.getCaretModel.moveToOffset(offset)

    val dataContext = new MockDataContext(psiFile, editor)
    try {
      executeAction(dataContext, editor)
      resultAfterAction(editor)
    } finally {
      editorManager.closeFile(psiFile.getVirtualFile)
    }
  }

  protected def executeAction(dataContext: DataContext, editor: Editor): Unit = {
    val action = ActionManager.getInstance.getAction(actionId)
    val e = new TestActionEvent(dataContext, action)
    action.beforeActionPerformedUpdate(e)
    if (e.getPresentation.isEnabled && e.getPresentation.isVisible) {
      action.actionPerformed(e)
    }
  }

  protected def resultAfterAction(editor: Editor): String
}
