package org.jetbrains.plugins.scala.base

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileTypes.FileType
import com.intellij.psi.PsiFile
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture
import org.jetbrains.plugins.scala.ScalaFileType
import org.jetbrains.plugins.scala.extensions.StringExt
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

  /////////////////////////////////////////////////////////
  // Section start: helper setup methods
  /////////////////////////////////////////////////////////
  //TODO: do not trim expected text here, trim it at usage place
  def checkResultByText(expectedFileText: String, ignoreTrailingSpaces: Boolean = true): Unit = {
    val expectedPatched = fileTextPatcher(expectedFileText.withNormalizedSeparator.trim)
    customCheckResultByTextFunction match {
      case Some(customCheck) =>
        customCheck(expectedPatched, ignoreTrailingSpaces)
      case _ =>
        javaFixture.checkResult(expectedPatched, ignoreTrailingSpaces)
    }
  }

  def configureFromFileText(fileText: String): PsiFile =
    configureFromFileText(defaultFileType, fileText)

  //TODO 1: do not trim expected text here, trim it at usage place
  def configureFromFileText(fileType: FileType, fileText: String): PsiFile = {
    val fileTextPatched = fileTextPatcher(fileText.withNormalizedSeparator.trim)
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
  /////////////////////////////////////////////////////////
  // Section end: helper setup methods
  /////////////////////////////////////////////////////////

  /////////////////////////////////////////////////////////
  // Section start: assertion methods
  /////////////////////////////////////////////////////////

  /////////////////////////////////////////////////////////
  // Section end: assertion methods
  /////////////////////////////////////////////////////////
}
