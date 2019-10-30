package org.jetbrains.plugins.scala.worksheet.integration

import org.jetbrains.plugins.scala.extensions.StringExt
import org.jetbrains.plugins.scala.util.assertions.MatcherAssertions
import org.jetbrains.plugins.scala.util.assertions.StringAssertions.assertStartsWith
import org.jetbrains.plugins.scala.worksheet.actions.topmenu.RunWorksheetAction.RunWorksheetActionResult
import org.jetbrains.plugins.scala.worksheet.integration.WorksheetIntegrationBaseTest.{TestRunResult, ViewerEditorData}
import org.jetbrains.plugins.scala.worksheet.processor.WorksheetCompiler
import org.jetbrains.plugins.scala.worksheet.ui.printers.WorksheetEditorPrinterFactory
import org.junit.Assert.{assertEquals, assertNotNull, assertTrue}

trait WorksheetRuntimeExceptionsTests extends MatcherAssertions {
  self: WorksheetIntegrationBaseTest =>

  def stackTraceLineStart: String

  def exceptionOutputShouldBeExpanded: Boolean

  // TODO: enhance the way stacktrace are tested
  def testDisplayFirstRuntimeException(
    leftText: String,
    expectedRightTextStart: String,
    expectedExceptionMessage: String
  ): Unit = {
    val leftEditor = doResultTest(leftText, RunWorksheetActionResult.Done)

    val ViewerEditorData(_, actualText, actualFoldings) = viewerEditorData(leftEditor)

    val (expectedTextStart, expectedFoldingsStart) = preprocessViewerText(expectedRightTextStart)

    val (actualTextStart, actualTextEnd) = actualText.splitAt(expectedTextStart.length)

    assertEquals(expectedTextStart, actualTextStart)

    val exceptionMessage :: exceptionOutputLines = actualTextEnd.linesIterator.toList
    assertEquals(expectedExceptionMessage, exceptionMessage)

    val (start, end) = exceptionOutputLines.splitAt(WorksheetEditorPrinterFactory.BULK_COUNT - 1)
    start.filter(_.nonEmpty).foreach { line =>
      assertStartsWith(line, stackTraceLineStart)
    }
    end.filter(_.nonEmpty).foreach { line =>
      assertEquals(line, WorksheetEditorPrinterFactory.END_MESSAGE.stripLineEnd)
    }

    val (foldingsStart, foldingsLast) = {
      val head :: tail = actualFoldings.toList.reverse
      (tail.reverse, head)
    }
    assertFoldings(expectedFoldingsStart, foldingsStart)

    assertEquals(expectedTextStart.length, foldingsLast.startOffset)
    assertEquals(actualText.trimRight.length, foldingsLast.endOffset)

    if (exceptionOutputShouldBeExpanded) {
      assertTrue("exception folding should be expanded", foldingsLast.isExpanded)

      val printer = worksheetCache.getPrinter(leftEditor).orNull
      assertNotNull("couldn't find editor printer", printer)

      val diffSplitter = printer.diffSplitter.orNull
      assertNotNull("couldn't find diff splitter", diffSplitter)

      val polygons = diffSplitter.renderedPolygons.toSeq.flatten
      assertTrue("worksheet diff splitter polygons were not drawn", polygons.size == 1)
    }
  }
}
