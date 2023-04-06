package org.jetbrains.plugins.scala.worksheet.integration

import com.intellij.openapi.editor.Editor
import org.jetbrains.plugins.scala.extensions.StringExt
import org.jetbrains.plugins.scala.util.assertions.MatcherAssertions
import org.jetbrains.plugins.scala.worksheet.actions.topmenu.RunWorksheetAction.RunWorksheetActionResult
import org.jetbrains.plugins.scala.worksheet.integration.WorksheetIntegrationBaseTest.ViewerEditorData
import org.jetbrains.plugins.scala.worksheet.integration.WorksheetRuntimeExceptionsTests.ExceptionFoldingType
import org.junit.Assert.{assertEquals, assertTrue, fail}

trait WorksheetRuntimeExceptionsTests extends MatcherAssertions {
  self: WorksheetIntegrationBaseTest =>

  def testDisplayFirstRuntimeException(
    leftText: String,
    expectedRightTextStart: String,
    exceptionFoldingType: ExceptionFoldingType,
    assertExceptionMessage: String => Unit
  ): Editor = {
    val leftEditor = doResultTest(leftText, RunWorksheetActionResult.Done).editor
    testDisplayFirstRuntimeException(leftEditor, expectedRightTextStart, exceptionFoldingType, assertExceptionMessage)
    leftEditor
  }

  def testDisplayFirstRuntimeException(
    leftEditor: Editor,
    expectedRightTextStart: String,
    exceptionFoldingType: ExceptionFoldingType,
    assertExceptionMessage: String => Unit
  ): Unit = {
    val ViewerEditorData(_, actualText, actualFoldings) = viewerEditorDataFromLeftEditor(leftEditor)

    val (expectedTextStart, expectedFoldingsStart) = preprocessViewerText(expectedRightTextStart)

    val (actualTextStart, actualTextEnd) = actualText.splitAt(expectedTextStart.length)

    assertEquals(expectedTextStart, actualTextStart)

    assertExceptionMessage(actualTextEnd)

    val (contentFoldings, exceptionFoldingOpt) = exceptionFoldingType match {
      case WorksheetRuntimeExceptionsTests.NoFolding => (actualFoldings, None)
      case WorksheetRuntimeExceptionsTests.Folded(_) => (actualFoldings.dropRight(1), actualFoldings.lastOption)
    }

    assertFoldings(expectedFoldingsStart, contentFoldings)

    exceptionFoldingType match {
      case WorksheetRuntimeExceptionsTests.Folded(expanded) =>
        val exceptionFolding = exceptionFoldingOpt.getOrElse(fail("exception output was expected to be folded").asInstanceOf[Nothing])
        assertEquals(expectedTextStart.length, exceptionFolding.startOffset)
        assertEquals(actualText.trimRight.length, exceptionFolding.endOffset)

        if (expanded) {
          assertTrue("exception folding should be expanded", exceptionFolding.isExpanded)
          val printer = worksheetCache.getPrinter(leftEditor).getOrElse(fail("couldn't find editor printer").asInstanceOf[Nothing])
          val diffSplitter = printer.diffSplitter.getOrElse(fail("couldn't find diff splitter").asInstanceOf[Nothing])
          val polygons = diffSplitter.renderedPolygons.toSeq.flatten
          assertTrue("worksheet diff splitter polygons were not drawn", polygons.size == 1)
        }
      case _ =>
    }
  }
}

object WorksheetRuntimeExceptionsTests {

  sealed trait ExceptionFoldingType
  object NoFolding extends ExceptionFoldingType
  final case class Folded(expanded: Boolean) extends ExceptionFoldingType
}
