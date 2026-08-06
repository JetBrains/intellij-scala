package org.jetbrains.plugins.scala.lang.actions

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.actionSystem.EditorActionHandler
import com.intellij.openapi.fileEditor.{FileEditorManager, OpenFileDescriptor}
import com.intellij.psi.PsiFile
import org.jetbrains.plugins.scala.base.NoSdkFileSetTestBase
import org.junit.Assert.assertNotNull

abstract class AbstractActionTestBase extends NoSdkFileSetTestBase with ActionTestBase {
  private var editor: Editor = _

  protected def createHandler: EditorActionHandler

  override protected def setUp(): Unit = {
    super.setUp()

    commonCodeStyleSettings.getIndentOptions.INDENT_SIZE = 2
    commonCodeStyleSettings.getIndentOptions.CONTINUATION_INDENT_SIZE = 2
    commonCodeStyleSettings.getIndentOptions.TAB_SIZE = 2
  }

  override protected def transform(testName: String, fileText: String): String = {
    val psiFile = createLightFile(fileText)
    processFile(psiFile)
  }

  private def processFile(file: PsiFile): String = {
    var result = ""

    var fileText = file.getText
    var offset = fileText.indexOf(CaretMarker)
    fileText = removeMarker(fileText)

    val psiFile = createLightFile(fileText)
    val fileEditorManager = FileEditorManager.getInstance(project)

    editor = fileEditorManager.openTextEditor(new OpenFileDescriptor(project, psiFile.getVirtualFile, 0), false)
    assertNotNull(editor)
    editor.getCaretModel.moveToOffset(offset)

    val dataContext = ActionTestBase.getDataContext(psiFile)
    val handler = createHandler

    try {
      val runnable: Runnable = () => handler.execute(editor, editor.getCaretModel.getCurrentCaret, dataContext)
      WriteCommandAction.runWriteCommandAction(project, runnable)
      offset = editor.getCaretModel.getOffset
      val editorText = editor.getDocument.getText
      result = editorText.substring(0, offset) + CaretMarker + editorText.substring(offset)
    } finally {
      fileEditorManager.closeFile(psiFile.getVirtualFile)
      editor = null
    }

    result
  }
}
