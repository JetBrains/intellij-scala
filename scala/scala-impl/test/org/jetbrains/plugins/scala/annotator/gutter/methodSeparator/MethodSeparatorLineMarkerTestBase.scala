package org.jetbrains.plugins.scala.annotator.gutter.methodSeparator

import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerImpl
import com.intellij.codeInsight.daemon.{DaemonCodeAnalyzerSettings, LineMarkerInfo}
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.markup.SeparatorPlacement
import com.intellij.psi.PsiElement
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import com.intellij.testFramework.fixtures.impl.JavaCodeInsightTestFixtureImpl
import org.jetbrains.plugins.scala.util.{RevertableChange, TestUtils}
import org.jetbrains.plugins.scala.{ScalaFileType, TypecheckerTests}
import org.junit.ComparisonFailure
import org.junit.experimental.categories.Category

import java.io.File
import java.util
import scala.io.Source
import scala.jdk.CollectionConverters._
import scala.util.Using

//NOTE: method separators are enabled in
//`File | Settings | Editor | General | Appearance | Show method separators`
@Category(Array(classOf[TypecheckerTests]))
abstract class MethodSeparatorLineMarkerTestBase extends LightJavaCodeInsightFixtureTestCase {
  private val SeparatorMarker = "// -"

  protected override def getBasePath = TestUtils.getTestDataPath + "/methodSeparator/"

  override def setUp(): Unit = {
    TestUtils.optimizeSearchingForIndexableFiles()

    super.setUp()
    myFixture.setTestDataPath(getBasePath)

    RevertableChange.withModifiedSetting(
      DaemonCodeAnalyzerSettings.getInstance
    )(true)(
      _.SHOW_METHOD_SEPARATORS,
      _.SHOW_METHOD_SEPARATORS = _
    ).applyChange(this)
  }

  def doTest(): Unit = {
    val testPath = s"$getBasePath${getTestName(false)}.test"
    val input = Using.resource(Source.fromFile(new File(testPath)))(_.getLines().mkString("\n"))

    myFixture.configureByText(ScalaFileType.INSTANCE, input.replaceAll(SeparatorMarker, ""))
    myFixture.asInstanceOf[JavaCodeInsightTestFixtureImpl].doHighlighting()

    val expectedMarkers = getSeparatorsFrom(input).sorted.sortBy(_._2)
    val actualMarkers = getSeparatorsFrom(getEditor.getDocument).sortBy(_._2)

    val expectedMarkersLineNumbers = expectedMarkers.map(_._2)
    val actualMarkersLineNumbers = actualMarkers.map(_._2)

    if (expectedMarkersLineNumbers != actualMarkersLineNumbers) {
      val expectedText = expectedMarkersLineNumbers.mkString("\n")
      val actualText = actualMarkers
        .map { case (marker, lineNumber) =>
          s"$lineNumber: $marker (element text: ${marker.getElement.getText})"
        }
        .mkString("\n")
      throw new ComparisonFailure("Line markers found on different lines", expectedText, actualText)
    }
  }

  private def getSeparatorsFrom(text: String): Seq[(String, Int)] = for {
    (lineText, lineIndex) <- text.split("\n").toSeq.zipWithIndex
    if lineText.contains(SeparatorMarker)
  } yield (lineText, lineIndex + 1)

  private def getSeparatorsFrom(document: Document): Seq[(LineMarkerInfo[PsiElement], Int)] = (for {
    marker <- DaemonCodeAnalyzerImpl.getLineMarkers(document, getProject).asInstanceOf[util.List[LineMarkerInfo[PsiElement]]].asScala
    if marker.separatorPlacement == SeparatorPlacement.TOP
  } yield {
    val markerElement = marker.getElement
    val offset = markerElement.getTextRange.getStartOffset
    val lineNumber = document.getLineNumber(offset) + 1
    (marker, lineNumber)
  }).toSeq
}
