package org.jetbrains.plugins.scala.codeInspection.implicits

import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.openapi.editor.SelectionModel
import com.intellij.openapi.util.TextRange
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestAdapter
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestAdapter.findCaretOffset
import org.junit.Assert.assertTrue

abstract class ImplicitParameterResolutionTestBase extends ScalaLightCodeInsightFixtureTestAdapter {

  import ImplicitParameterResolutionTestBase._

  protected val classOfInspection: Class[_ <: LocalInspectionTool]

  protected final def checkTextHasNoErrors(text: String, description: String): Unit = {
    val ranges = configureByText(text).collect {
      case (info, range) if info.getDescription == description => range
    }
    assertTrue(s"Highlights found at: ${ranges.mkString(", ")}.", ranges.isEmpty)
  }

  protected final def checkTextHasError(text: String, description: String): Unit = {
    val ranges = configureByText(text).collect {
      case (info, range) if info.getDescription == description => range
    }
    assertTrue(s"Highlights not found: $description", ranges.nonEmpty)

    val selection = selectedRange(getEditor.getSelectionModel)
    assertTrue(s"Highlights found at: ${ranges.mkString(", ")}, not found: $selection", ranges.contains(selection))
  }

  protected final def configureByText(text: String): Seq[(HighlightInfo, TextRange)] = {
    val (normalizedText, offset) = findCaretOffset(text, stripTrailingSpaces = true)

    val fixture = getFixture
    fixture.configureByText("dummy.scala", normalizedText)
    fixture.enableInspections(classOfInspection)

    import scala.collection.JavaConverters._
    fixture
      .doHighlighting()
      .asScala
      .map(info => (info, highlightedRange(info)))
      .filter(checkOffset(_, offset))
  }

}

object ImplicitParameterResolutionTestBase {
  private def highlightedRange(info: HighlightInfo): TextRange =
    new TextRange(info.getStartOffset, info.getEndOffset)

  private def selectedRange(model: SelectionModel): TextRange =
    new TextRange(model.getSelectionStart, model.getSelectionEnd)

  private def checkOffset(pair: (HighlightInfo, TextRange), offset: Int): Boolean = pair match {
    case _ if offset == -1 => true
    case (_, range)        => range.contains(offset)
  }
}
