package org.jetbrains.plugins.scala.refactoring
package extractMethod

import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.fileEditor.{FileEditorManager, OpenFileDescriptor}
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.{CharsetToolkit, LocalFileSystem}
import com.intellij.psi.{PsiElement, PsiFile}
import com.intellij.testFramework.UsefulTestCase
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestCase
import org.jetbrains.plugins.scala.extensions.StringExt
import org.jetbrains.plugins.scala.lang.formatting.settings.ScalaCodeStyleSettings
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.refactoring.extractMethod.ScalaExtractMethodHandler
import org.jetbrains.plugins.scala.util.TypeAnnotationSettings
import org.junit.Assert._

import java.io.File

abstract class ScalaExtractMethodTestBase extends ScalaLightCodeInsightFixtureTestCase {
  private val startMarker = "/*start*/"
  private val endMarker = "/*end*/"
  private val scopeMarker = "/*inThisScope*/"

  def folderPath: String = refactoringCommonTestDataRoot + "extractMethod/"

  protected def doTest(
    settings: ScalaCodeStyleSettings = TypeAnnotationSettings.alwaysAddType(ScalaCodeStyleSettings.getInstance(getProject))
  ): Unit = withSettings(settings, getProject) {
    val fileName = s"${getTestName(false)}.scala"
    val filePath = s"$folderPath$fileName".replace(File.separatorChar, '/')
    val vFile = LocalFileSystem.getInstance.findFileByPath(filePath)
    assert(vFile != null, s"file $filePath not found")

    val (fileText, scopeOffset, startOffset, endOffset) = extractFileContentText(filePath)

    configureFromFileText(fileName, fileText)
    val scalaFile = getFile.asInstanceOf[ScalaFile]

    invokeExtractMethodRefactoring(scalaFile, scopeOffset, startOffset, endOffset)(getProject)

    val lastPsi = scalaFile.findElementAt(scalaFile.getText.length - 1)

    val actual = extractActualResult(scalaFile, lastPsi).trim
    val expected = extractExpectedResult(lastPsi)
    assertEquals(expected, actual)
  }

  private val chosenTargetScopeKey: DataKey[Int] = DataKey.create("chosenTargetScope")

  private def invokeExtractMethodRefactoring(scalaFile: ScalaFile, scopeOffset: Int, startOffset: Int, endOffset: Int)
                                            (project: Project): Unit = {
    val editor = openEditorAtOffset(startOffset)

    editor.getSelectionModel.setSelection(startOffset, endOffset)

    val context = SimpleDataContext.getSimpleContext(chosenTargetScopeKey, scopeOffset)
    val handler = new ScalaExtractMethodHandler
    handler.invoke(project, getEditor, scalaFile, context)
    UsefulTestCase.doPostponedFormatting(project)
  }

  private def withSettings(settings: ScalaCodeStyleSettings, project: Project)(body: => Unit): Unit = {
    val oldSettings = ScalaCodeStyleSettings.getInstance(project).clone()
    try {
      TypeAnnotationSettings.set(project, settings)
      body
    } finally {
      TypeAnnotationSettings.set(project, oldSettings.asInstanceOf[ScalaCodeStyleSettings])
    }
  }

  private def extractActualResult(file: PsiFile, lastPsi: PsiElement) = {
    file.getText.substring(0, lastPsi.getTextOffset).trim
  }

  private def extractExpectedResult(lastPsi: PsiElement) = {
    import ScalaTokenTypes._
    val text = lastPsi.getText
    lastPsi.getNode.getElementType match {
      case `tLINE_COMMENT`                   => text.substring(2).trim
      case `tBLOCK_COMMENT` | `tDOC_COMMENT` => text.substring(2, text.length - 2).trim
      case _                                 => fail("Test result must be in last comment statement.").asInstanceOf[Nothing]
    }
  }

  private def extractFileContentText(filePath: String): (String, Int, Int, Int) = {
    val vFile = LocalFileSystem.getInstance.findFileByPath(filePath)
    assert(vFile != null, s"file $filePath not found")

    var fileText = FileUtil.loadFile(new File(vFile.getCanonicalPath), CharsetToolkit.UTF8).withNormalizedSeparator

    val scopeOffset = fileText.indexOf(scopeMarker)

    if (scopeOffset != -1)
      fileText = fileText.replace(scopeMarker, "")

    val startOffset = fileText.indexOf(startMarker)
    assert(startOffset != -1, "Not specified start marker in test case. Use /*start*/ in scala file for this.")
    fileText = fileText.replace(startMarker, "")

    val endOffset = fileText.indexOf(endMarker)
    assert(endOffset != -1, "Not specified end marker in test case. Use /*end*/ in scala file for this.")
    fileText = fileText.replace(endMarker, "")

    (fileText, scopeOffset, startOffset, endOffset)
  }
}