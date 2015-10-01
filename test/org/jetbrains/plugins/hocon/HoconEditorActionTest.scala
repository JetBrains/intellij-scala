package org.jetbrains.plugins.hocon

import com.intellij.openapi.actionSystem._
import com.intellij.openapi.editor.actionSystem.EditorActionManager
import com.intellij.openapi.fileEditor.{FileEditorManager, OpenFileDescriptor}
import com.intellij.psi.PsiFile

/**
 * @author ghik
 */
abstract class HoconEditorActionTest(actionId: String, subpath: String)
  extends HoconFileSetTestCase("actions/" + subpath) {

  // Code based on AbstractEnterActionTestBase

  private class MockDataContext(file: PsiFile) extends DataContext with DataProvider {
    def getData(dataId: String): AnyRef =
      if (LangDataKeys.LANGUAGE is dataId) file.getLanguage
      else if (CommonDataKeys.PROJECT is dataId) file.getProject
      else null
  }

  protected def transform(data: Seq[String]): String = {
    val (fileText, offset) = extractCaret(data.head)
    val psiFile = HoconTestUtils.createPseudoPhysicalHoconFile(getProject, fileText)

    val editorManager = FileEditorManager.getInstance(getProject)
    val editor = editorManager.openTextEditor(new OpenFileDescriptor(getProject, psiFile.getVirtualFile, 0), false)
    assert(editor != null)
    editor.getCaretModel.moveToOffset(offset)

    val actionHandler = EditorActionManager.getInstance.getActionHandler(actionId)
    val dataContext = new MockDataContext(psiFile)
    assert(actionHandler != null)

    try {
      inWriteCommandAction {
        actionHandler.execute(editor, editor.getCaretModel.getCurrentCaret, dataContext)
      }

      insertCaret(editor.getDocument.getText, editor.getCaretModel.getOffset)
    } finally {
      editorManager.closeFile(psiFile.getVirtualFile)
    }
  }
}
