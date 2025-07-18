package org.jetbrains.plugins.scala.lang.parser

import com.intellij.codeInsight.daemon.impl.HighlightInfo
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestCase
import org.junit.Assert.fail

import scala.jdk.CollectionConverters.ListHasAsScala

/**
 * Indirectly tests the logic of [[com.intellij.codeInsight.highlighting.LargeFilesAnnotator]]
 */
class LargeFilesAnnotatorTest extends ScalaLightCodeInsightFixtureTestCase {

  /**
   * @see [[org.jetbrains.plugins.scala.lang.parser.PsiFileFactoryForDecompiledScalaFileTest.testTreatTooLargeScalaFilesAsPlainTextFiles]]
   */
  def testShowEditorWarningInLargeScalaFiles(): Unit = {
    val fileText = PsiFileFactoryTest.generateLargeScalaFileWithLargeCommentText
    myFixture.configureByText("Dummy.scala", fileText)
    val highlightInfos = myFixture.doHighlighting().asScala

    val messageRegex = """The file size \(.*\) exceeds the configured limit \(.*\). Code insight features are not available.""".r
    val attributesKey = com.intellij.openapi.editor.colors.CodeInsightColors.WARNINGS_ATTRIBUTES
    val isFileLevelAnnotation = true

    val foundInfo = highlightInfos.find { info =>
      messageRegex.matches(info.getDescription) &&
        info.forcedTextAttributesKey == attributesKey &&
        info.isFileLevelAnnotation == isFileLevelAnnotation
    }
    if (foundInfo.isEmpty) {
      fail(
        s"""Couldn't find highlighting matching criteria:
           |Description (regexp): $messageRegex
           |Attributes key: $attributesKey
           |Is file level annotation: $isFileLevelAnnotation
           |Available highlightings:
           |${highlightInfos.map(highlightingDebugText).mkString("\n")}
           |""".stripMargin
      )
    }
  }

  private def highlightingDebugText(info: HighlightInfo): String = {
    s"[${info.forcedTextAttributesKey}] [fileLevel=${info.isFileLevelAnnotation}] ${info.getDescription}"
  }
}
