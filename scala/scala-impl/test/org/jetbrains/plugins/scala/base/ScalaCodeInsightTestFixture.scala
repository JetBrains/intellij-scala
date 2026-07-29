package org.jetbrains.plugins.scala.base

import com.intellij.openapi.editor.{Editor, EditorCopyPasteHelper}
import com.intellij.openapi.fileTypes.FileType
import com.intellij.psi.PsiFile
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture
import org.intellij.lang.annotations.Language
import org.jetbrains.plugins.scala.ScalaFileType
import org.jetbrains.plugins.scala.extensions.{StringExt, inWriteCommandAction}
import org.junit.Assert.assertNotNull

//NOTE: for now we intentionally inherit any base text feature (e.g. JavaCodeInsightTestFixture) and use composition instead.
//This is done "for simplicity" of transition from inheritance to fixture usage in tests
//Once it's stabilised we might consider using the inheritance, but we need to ensure it will beneficial at all
final class ScalaCodeInsightTestFixture(
  val javaFixture: JavaCodeInsightTestFixture
) {
  private var fileTextPatcher: String => String = identity
  private var defaultFileType: FileType = ScalaFileType.INSTANCE
  private var customCheckResultByTextFunction: Option[(String, Boolean) => Unit] = None

  def setFileTextPatcher(patcher: String => String): Unit =
    fileTextPatcher = patcher

  def setDefaultFileType(fileType: FileType): Unit = {
    defaultFileType = fileType
  }

  def setCustomCheckResultByTextFunction(f: (String, Boolean) => Unit): Unit = {
    customCheckResultByTextFunction = Some(f)
  }

  //region helper setup methods
  def checkResultByText(expectedFileText: String, ignoreTrailingSpaces: Boolean = true): Unit = {
    val expectedPatched = fileTextPatcher(expectedFileText.withNormalizedSeparator)
    customCheckResultByTextFunction match {
      case Some(customCheck) =>
        customCheck(expectedPatched, ignoreTrailingSpaces)
      case _ =>
        javaFixture.checkResult(expectedPatched, ignoreTrailingSpaces)
    }
  }

  def configureFromFileText(fileText: String): PsiFile =
    configureFromFileText(defaultFileType, fileText)

  def configureFromFileText(fileType: FileType, fileText: String): PsiFile = {
    val fileTextPatched = fileTextPatcher(fileText.withNormalizedSeparator)
    val file = javaFixture.configureByText(fileType, fileTextPatched)
    assertNotNull(file)
    file
  }

  def configureFromFileTextWithSomeName(fileType: String, fileText: String): PsiFile = {
    val fileTextPatched = fileTextPatcher(fileText.withNormalizedSeparator)
    val file = javaFixture.configureByText("Test." + fileType, fileTextPatched)
    assertNotNull(file)
    file
  }

  def configureFromFileText(fileName: String, fileText: String): PsiFile = {
    val fileTextPatched = fileTextPatcher(fileText.withNormalizedSeparator)
    val file = javaFixture.configureByText(fileName: String, fileTextPatched)
    assertNotNull(file)
    file
  }

  def openEditorAtOffset(startOffset: Int): Editor = {
    import com.intellij.openapi.fileEditor.{FileEditorManager, OpenFileDescriptor}
    val project = javaFixture.getProject
    val editorManager = FileEditorManager.getInstance(project)
    val vFile = javaFixture.getFile.getVirtualFile
    val editor = editorManager.openTextEditor(new OpenFileDescriptor(project, vFile, startOffset), false)
    editor
  }
  //endregion helper setup methods

  //region assertion methods

  /**
   * Opens an empty dummy file in editor and uses [[EditorCopyPasteHelper.pasteFromClipboard]] to get clipboard contents.</br>
   * <b>Any previously opened files might need to be reopened after calling this function.</b>
   */
  def checkClipboardContent(expectedClipboardContent: String, ignoreTrailingSpaces: Boolean = false): Unit = {
    val dummyFile = javaFixture.addFileToProject("clipboard_content_assertion_file.txt", "")
    javaFixture.openFileInEditor(dummyFile.getVirtualFile)
    val pastedRanges = inWriteCommandAction {
      EditorCopyPasteHelper.getInstance().pasteFromClipboard(javaFixture.getEditor)
    }(javaFixture.getProject)
    assertNotNull("Paste from clipboard failed", pastedRanges)
    javaFixture.checkResult(expectedClipboardContent, ignoreTrailingSpaces)
  }
  //end region assertion methods
}
