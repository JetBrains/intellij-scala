package org.jetbrains.plugins.scala.annotator.gutter

import org.jetbrains.plugins.scala.util.TestUtils
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase
import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerImpl
import com.intellij.testFramework.fixtures.impl.JavaCodeInsightTestFixtureImpl
import com.intellij.openapi.project.Project
import org.junit.Assert._
import scala.collection.JavaConversions._
import org.jetbrains.plugins.scala.ScalaFileType
import com.intellij.psi.PsiElement
import com.intellij.openapi.editor.markup.SeparatorPlacement
import com.intellij.openapi.editor.{Document, Editor}
import collection.mutable.ArrayBuffer
import com.intellij.codeInsight.daemon.{DaemonCodeAnalyzerSettings, LineMarkerInfo}

/**
 * Pavel.Fatin, 14.01.2010
 */

abstract class AbstractLineMarkerTest extends LightCodeInsightFixtureTestCase {
  val marker = "// -"

  protected override def getBasePath = TestUtils.getTestDataPath + "/methodSeparator/"


  override def setUp = {
    super.setUp
    myFixture.setTestDataPath(getBasePath)
  }

  def doTest() = {
    val sections = TestUtils.readInput(getBasePath + getTestName(false) + ".test")
    val input = sections.get(0)
    myFixture.configureByText(ScalaFileType.SCALA_FILE_TYPE, input.replaceAll(marker, ""))

    DaemonCodeAnalyzerSettings.getInstance.SHOW_METHOD_SEPARATORS = true
    (myFixture.asInstanceOf[JavaCodeInsightTestFixtureImpl]).doHighlighting

    val expected = getSeparatorsFrom(input)
    val actual = getSeparatorsFrom(myFixture.getEditor, myFixture.getProject)
    assertEquals(expected.mkString(", "), actual.mkString(", "))
  }

  def getSeparatorsFrom(text: String) = {
    for{(line, i) <- text.split("\n").zipWithIndex
      if line.contains(marker)} yield i + 1
  }

  def getSeparatorsFrom(editor: Editor, project: Project) = {
    val separators = for{each <- DaemonCodeAnalyzerImpl.getLineMarkers(editor.getDocument, project)
                         if each.separatorPlacement == SeparatorPlacement.TOP
                         index = editor.getDocument.getLineNumber(each.getElement.getTextOffset)}
                     yield index + 1
    separators.sortWith(_ < _)
  }
}
