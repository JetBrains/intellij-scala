package org.jetbrains.plugins.scala.refactoring.extractMethod

import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.application.impl.NonBlockingReadActionImpl
import com.intellij.openapi.project.Project
import com.intellij.psi.{PsiElement, PsiFile}
import com.intellij.testFramework.UsefulTestCase
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestCase
import org.jetbrains.plugins.scala.extensions.{PathExt, StringExt}
import org.jetbrains.plugins.scala.lang.formatting.settings.ScalaCodeStyleSettings
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.refactoring.extractMethod.ScalaExtractMethodHandler
import org.jetbrains.plugins.scala.refactoring.refactoringCommonTestDataRoot
import org.jetbrains.plugins.scala.util.{TestUtils, TypeAnnotationSettings}
import org.junit.Assert._

import java.nio.charset.StandardCharsets
import java.nio.file.Path

abstract class ScalaExtractMethodTestBase extends ScalaLightCodeInsightFixtureTestCase {

  private val startMarker = "/*start*/"
  private val endMarker = "/*end*/"
  private val scopeMarker = "/*inThisScope*/"

  def folderPath: Path = refactoringCommonTestDataRoot / "extractMethod"

  protected def doTest(
    settings: ScalaCodeStyleSettings = TypeAnnotationSettings.alwaysAddType(ScalaCodeStyleSettings.getInstance(getProject))
  ): Unit = withSettings(settings, getProject) {
    val testName = getTestName(false)

    val (fileName, filePath) = {
      //support ordinary scala files and scala worksheets
      val name1 = s"$testName.scala"
      val path1 = folderPath / name1
      val file1Exists = path1.exists

      val name2 = s"$testName.sc"
      val path2 = folderPath / name2
      val file2Exists = path2.exists


      assertTrue(s"file for $testName not found in $folderPath", file1Exists || file2Exists)

      if (file1Exists) (name1, path1)
      else (name2, path2)
    }

    val (fileText, scopeOffset, startOffset, endOffset) = extractFileContentText(filePath)

    configureFromFileText(fileName, fileText)
    val scalaFile = getFile.asInstanceOf[ScalaFile]

    invokeExtractMethodRefactoring(scalaFile, scopeOffset, startOffset, endOffset)(getProject)
    NonBlockingReadActionImpl.waitForAsyncTaskCompletion()

    val lastPsi = scalaFile.findElementAt(scalaFile.getText.length - 1)

    val actual = extractActualResult(scalaFile, lastPsi).trim
    val expected = TestUtils.extractExpectedResultFromLastComment(getFile).expectedResult
    assertEquals(expected, actual)
  }

  private def invokeExtractMethodRefactoring(scalaFile: ScalaFile, scopeOffset: Int, startOffset: Int, endOffset: Int)
                                            (project: Project): Unit = {
    val editor = openEditorAtOffset(startOffset)

    editor.getSelectionModel.setSelection(startOffset, endOffset)

    val context = SimpleDataContext.getSimpleContext(ScalaExtractMethodHandler.ChosenTargetScopeKey, scopeOffset)
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

  private def extractFileContentText(filePath: Path): (String, Int, Int, Int) = {
    assert(filePath.exists, s"file $filePath not found")
    var fileText = filePath.readAllBytesToString(StandardCharsets.UTF_8).withNormalizedSeparator

    var scopeOffset = fileText.indexOf(scopeMarker)

    if (scopeOffset != -1)
      fileText = fileText.replace(scopeMarker, "")

    val startOffset = fileText.indexOf(startMarker)
    assert(startOffset != -1, "Not specified start marker in test case. Use /*start*/ in scala file for this.")
    fileText = fileText.replace(startMarker, "")
    if (scopeOffset > startOffset) scopeOffset -= startMarker.length

    val endOffset = fileText.indexOf(endMarker)
    assert(endOffset != -1, "Not specified end marker in test case. Use /*end*/ in scala file for this.")
    fileText = fileText.replace(endMarker, "")
    if (scopeOffset > endOffset) scopeOffset -= endMarker.length

    (fileText, scopeOffset, startOffset, endOffset)
  }
}
