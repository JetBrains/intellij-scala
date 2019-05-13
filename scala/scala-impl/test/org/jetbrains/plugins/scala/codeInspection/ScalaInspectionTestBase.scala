package org.jetbrains.plugins.scala
package codeInspection

import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.openapi.editor.SelectionModel
import com.intellij.openapi.util.TextRange
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestAdapter
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestAdapter.{findCaretOffset, normalize}
import org.jetbrains.plugins.scala.extensions.executeWriteActionCommand
import org.junit.Assert._

import scala.collection.JavaConverters

/**
  * Nikolay.Tropin
  * 6/3/13
  */
abstract class ScalaInspectionTestBase extends ScalaLightCodeInsightFixtureTestAdapter {

  import ScalaInspectionTestBase._

  protected val classOfInspection: Class[_ <: LocalInspectionTool]
  protected val description: String

  protected override def setUp(): Unit = {
    super.setUp()
    getFixture.enableInspections(classOfInspection)
  }

  protected def descriptionMatches(s: String): Boolean = s == normalize(description)

  protected override final def checkTextHasNoErrors(text: String): Unit = {
    val ranges = findRanges(text)
    assertTrue(if (shouldPass) s"Highlights found at: ${ranges.mkString(", ")}." else failingPassed,
      !shouldPass ^ ranges.isEmpty)
  }

  protected final def checkTextHasError(text: String, allowAdditionalHighlights: Boolean = false): Unit = {
    val foundRanges = findRanges(text)
    val expectedHighlightRange = selectedRange(getEditor.getSelectionModel)
    if (shouldPass) {
      assertTrue(s"Highlights not found: $description", foundRanges.nonEmpty)
      assertTrue(s"Highlights found at: ${foundRanges.mkString(", ")}, not found: $expectedHighlightRange", foundRanges.contains(expectedHighlightRange))
      val duplicatedHighlights = foundRanges
        .groupBy(identity)
        .mapValues(_.length)
        .toSeq
        .collect { case (highlight, count) if count > 1 => highlight }

      assertTrue(s"Some highlights were duplicated: ${duplicatedHighlights.mkString(", ")}", duplicatedHighlights.isEmpty)
      if (!allowAdditionalHighlights) {
        assertTrue(s"Found too many highlights: ${foundRanges.mkString(", ")}", foundRanges.length == 1)
      }
    } else {
      assertTrue(failingPassed, foundRanges.isEmpty)
      assertFalse(failingPassed, foundRanges.contains(expectedHighlightRange))
    }
  }

  protected final def configureByText(text: String): Seq[(HighlightInfo, TextRange)] = {
    val fileText = createTestText(text)
    val (normalizedText, offset) = findCaretOffset(fileText, stripTrailingSpaces = true)

    val fixture = getFixture
    fixture.configureByText("dummy.scala", normalizedText)

    import JavaConverters._
    fixture.doHighlighting().asScala
      .filter(it => descriptionMatches(it.getDescription))
      .map(info => (info, highlightedRange(info)))
      .filter(checkOffset(_, offset))
  }

  protected def createTestText(text: String): String = text

  private def findRanges(text: String): Seq[TextRange] = configureByText(text).map(_._2)
}

object ScalaInspectionTestBase {
  private def highlightedRange(info: HighlightInfo): TextRange =
    new TextRange(info.getStartOffset, info.getEndOffset)

  private def selectedRange(model: SelectionModel): TextRange =
    new TextRange(model.getSelectionStart, model.getSelectionEnd)

  private def checkOffset(pair: (HighlightInfo, TextRange), offset: Int): Boolean = pair match {
    case _ if offset == -1 => true
    case (_, range) => range.contains(offset)
  }
}

abstract class ScalaQuickFixTestBase extends ScalaInspectionTestBase {

  import ScalaQuickFixTestBase._

  protected final def testQuickFix(text: String, expected: String, hint: String): Unit = {
    val maybeAction = findQuickFix(text, hint)
    assertFalse(s"Quick fix not found: $hint", maybeAction.isEmpty)

    executeWriteActionCommand() {
      maybeAction.get.invoke(getProject, getEditor, getFile)
    }(getProject)

    val expectedFileText = createTestText(expected)
    getFixture.checkResult(normalize(expectedFileText), /*stripTrailingSpaces = */ true)
  }

  protected final def checkNotFixable(text: String, hint: String): Unit = {
    val maybeAction = findQuickFix(text, hint)
    assertTrue("Quick fix found.", maybeAction.isEmpty)
  }

  private def findQuickFix(text: String, hint: String): Option[IntentionAction] =
    configureByText(text).map(_._1) match {
      case Seq() =>
        fail("Errors not found.")
        null
      case seq => seq.flatMap(quickFixes).find(_.getText == hint)
    }
}

object ScalaQuickFixTestBase {
  private def quickFixes(info: HighlightInfo): Seq[IntentionAction] = {
    import JavaConverters._
    Option(info.quickFixActionRanges).toSeq
      .flatMap(_.asScala)
      .flatMap(pair => Option(pair))
      .map(_.getFirst.getAction)
  }
}
