package org.jetbrains.plugins.scala
package codeInspection

import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.openapi.editor.SelectionModel
import com.intellij.openapi.util.TextRange
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestAdapter
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestAdapter.{findCaretOffset, normalize}
import org.jetbrains.plugins.scala.extensions.startCommand
import org.junit.Assert._

/**
  * Nikolay.Tropin
  * 6/3/13
  */
abstract class ScalaInspectionTestBase extends ScalaLightCodeInsightFixtureTestAdapter {

  import ScalaInspectionTestBase._

  protected val classOfInspection: Class[_ <: LocalInspectionTool]
  protected val description: String

  protected override final def checkTextHasNoErrors(text: String): Unit = {
    val ranges = configureByText(text).map(_._2)
    assertTrue(s"Highlights found at: ${ranges.mkString(", ")}.", ranges.isEmpty)
  }

  protected final def checkTextHasError(text: String): Unit = {
    val ranges = configureByText(text).map(_._2)
    assertTrue(s"Highlights not found: $description", ranges.nonEmpty)

    val range = selectedRange(getEditor.getSelectionModel)
    assertTrue(s"Highlights found at: ${ranges.mkString(", ")}, not found: $range", ranges.contains(range))
  }

  protected final def configureByText(text: String): Seq[(HighlightInfo, TextRange)] = {
    val (normalizedText, offset) = findCaretOffset(text, stripTrailingSpaces = true)

    val fixture = getFixture
    fixture.configureByText("dummy.scala", normalizedText)
    fixture.enableInspections(classOfInspection)

    val description = normalize(this.description)

    import scala.collection.JavaConversions._
    fixture.doHighlighting()
      .filter(_.getDescription == description)
      .map(info => (info, highlightedRange(info)))
      .filter(checkOffset(_, offset))
  }
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

  protected final def testQuickFix(text: String, expected: String, hint: String): Unit = {
    val highlights = configureByText(text).map(_._1)

    import ScalaQuickFixTestBase._
    val actions = highlights.flatMap(quickFixes)
    assertFalse("Quick fix not found.", actions.isEmpty)

    val action = actions.find(_.getText == hint).orNull
    assertNotNull(s"Quick fix not found: $hint", action)

    startCommand(getProject) {
      action.invoke(getProject, getEditor, getFile)
    }
    getFixture.checkResult(normalize(expected), /*stripTrailingSpaces = */ true)
  }
}

object ScalaQuickFixTestBase {
  private def quickFixes(info: HighlightInfo): Seq[IntentionAction] = {
    import scala.collection.JavaConversions._
    Option(info.quickFixActionRanges).toSeq.flatten
      .flatMap(pair => Option(pair))
      .map(_.getFirst.getAction)
  }
}
