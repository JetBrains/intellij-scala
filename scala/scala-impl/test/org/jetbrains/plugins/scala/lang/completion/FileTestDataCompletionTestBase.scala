package org.jetbrains.plugins.scala.lang.completion

import com.intellij.codeInsight.completion.{CodeCompletionHandlerBase, CompletionType}
import com.intellij.codeInsight.lookup.impl.LookupImpl
import com.intellij.codeInsight.lookup.{LookupElement, LookupElementPresentation, LookupManager}
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestCase
import org.jetbrains.plugins.scala.extensions.{PathExt, StringExt}
import org.jetbrains.plugins.scala.util.TestUtils
import org.jetbrains.plugins.scala.{CompletionTests, ScalaVersion}
import org.junit.Assert._
import org.junit.experimental.categories.Category

import java.nio.charset.StandardCharsets
import java.nio.file.Path

@Category(Array(classOf[CompletionTests]))
abstract class FileTestDataCompletionTestBase extends ScalaLightCodeInsightFixtureTestCase {
  // Must be lazy so it can be overridden without early initializers
  protected lazy val caretMarker = "/*caret*/"
  protected lazy val extension: String = "scala"

  def folderPath: Path = Path.of(getTestDataPath, "completion")

  override protected def supportedIn(version: ScalaVersion): Boolean = version >= ScalaVersion.Latest.Scala_2_13

  protected def checkResult(variants: Array[String], expected: String): Unit = {
    val actual = variants.sortWith(_ < _)
      .mkString("\n").trim
    assertEquals(expected, actual)
  }

  protected def doTest(): Unit = {
    val fileName = getTestName(false) + s".$extension"
    val filePath = folderPath / fileName

    val fileText = filePath
      .readAllBytesToString(StandardCharsets.UTF_8)
      .withNormalizedSeparator

    configureFromFileText(fileName, fileText)

    val offset = fileText.indexOf(caretMarker) match {
      case -1 => throw new AssertionError(s"Not specified end marker in test case. Use $caretMarker in scala file for this.")
      case index => index
    }

    val editor = openEditorAtOffset(offset)

    val completionType = if (fileName.startsWith("Smart")) CompletionType.SMART else CompletionType.BASIC
    new CodeCompletionHandlerBase(
      completionType,
      false,
      false,
      true
    ).invokeCompletion(getProject, editor)

    val items = LookupManager.getActiveLookup(editor) match {
      case lookup: LookupImpl =>
        val items = lookup.getItems.toArray(LookupElement.EMPTY_ARRAY)
        // TODO: test completion items presentations instead of just getLookupString
        //  something like:
        //  val presentations: Array[LookupElementPresentation] = items.map { item =>
        //    val presentation = new LookupElementPresentation
        //    item.renderElement(presentation)
        //    presentation
        //  }
        //  val itemTexts = presentations.map(_.getItemText)
        val lookups = items.map { item =>
          val presentation = new LookupElementPresentation
          item.renderElement(presentation)

          var result = item.getLookupString
          if (presentation.isStrikeout) {
            result += " (strikeout)"
          }
          result
        }
        lookups
      case _ => Array.empty[String]
    }

    val expected = TestUtils.extractExpectedResultFromLastComment(getFile).expectedResult
    checkResult(items, expected)
  }
}
