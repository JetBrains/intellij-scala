package org.jetbrains.plugins.scala.codeInspection

import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.ide.scratch.ScratchRootType
import com.intellij.openapi.fileTypes.LanguageFileType
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile
import org.jetbrains.plugins.scala.ScalaFileType
import org.jetbrains.plugins.scala.base.{ScalaLightCodeInsightFixtureTestAdapter, SharedTestProjectToken}
import org.jetbrains.plugins.scala.extensions.{HighlightInfoExt, StringExt}
import org.jetbrains.plugins.scala.util.FindCaretOffset.findCaretOffset
import org.jetbrains.plugins.scala.util.MarkersUtils
import org.junit.Assert.assertTrue

import scala.jdk.CollectionConverters.CollectionHasAsScala

abstract class ScalaHighlightsTestBase extends ScalaLightCodeInsightFixtureTestAdapter {
  self: ScalaLightCodeInsightFixtureTestAdapter =>

  import ScalaHighlightsTestBase._

  protected val description: String

  protected val fileType: LanguageFileType = ScalaFileType.INSTANCE
  protected val isScratchFile: Boolean = false

  override protected def sharedProjectToken = SharedTestProjectToken(this.getClass)

  protected def descriptionMatches(s: String): Boolean = s == description.withNormalizedSeparator.trim

  protected override def checkTextHasNoErrors(text: String): Unit = {
    val TestPrepareResult(fileText, _, highlights) = configureByText(text)
    val ranges = highlights.map(_.range)

    def rangeText = ranges.mkString(", ")

    assertTrue(
      if (shouldPass) s"Highlights found at: $rangeText:\n${highlightsDebugText(highlights, fileText)}"
      else failingPassed,
      !shouldPass ^ ranges.isEmpty
    )
  }

  private def highlightsDebugText(highlights: Seq[HighlightInfo], fileText: String): String = {
    val strings = highlights.map(highlightsDebugText(_, fileText))
    val indent = "  "
    strings.mkString(indent, indent + "\n", "")
  }

  private def highlightsDebugText(info: HighlightInfo, fileText: String): String = {
    val range = info.range
    val rangeText = fileText.substring(range.getStartOffset, range.getEndOffset)
    s"$range[$rangeText]: ${info.getDescription}"
  }

  protected def checkTextHasError(text: String, allowAdditionalHighlights: Boolean = false): Unit = {
    val TestPrepareResult(fileText, expectedRanges, actualHighlights) = configureByText(text)
    val actualRanges = actualHighlights
    checkTextHasError(expectedRanges, actualRanges, allowAdditionalHighlights, fileText)
  }

  protected def checkTextHasError(expectedHighlights: Seq[ExpectedHighlight],
                                  actualHighlights: Seq[HighlightInfo],
                                  allowAdditionalHighlights: Boolean,
                                  fileText: String): Unit = {
    val expectedHighlightRanges = expectedHighlights.map(_.range)
    val actualHighlightRanges = actualHighlights.map(_.range)

    val expectedRangesNotFound = expectedHighlightRanges.filterNot(actualHighlightRanges.contains)
    if (shouldPass) {
      assertTrue(
        s"Highlights not found: $description",
        actualHighlightRanges.nonEmpty
      )
      assertTrue(
        s"""Highlights found at: ${actualHighlightRanges.mkString(", ")}
           |not found: ${expectedRangesNotFound.mkString(", ")}""".stripMargin,
        expectedRangesNotFound.isEmpty
      )

      assertNoDuplicates(actualHighlights, fileText)

      if (!allowAdditionalHighlights) {
        assertTrue(
          s"""Found too many highlights:
             |${highlightsDebugText(actualHighlights, fileText)}
             |expected: ${expectedHighlightRanges.mkString(", ")}""".stripMargin,
          actualHighlightRanges.length == expectedHighlightRanges.length
        )
      }
    } else {
      assertTrue(failingPassed, actualHighlightRanges.isEmpty)
      assertTrue(failingPassed, expectedRangesNotFound.nonEmpty)
    }
  }

  private def assertNoDuplicates(highlights: Seq[HighlightInfo], fileText: String): Unit = {
    val duplicatedHighlights = highlights
      .groupBy(_.range).toSeq
      .collect { case (_, highlights) if highlights.size > 1 => highlights }
      .flatten
    assertTrue(
      s"Some highlights were duplicated:\n${highlightsDebugText(duplicatedHighlights, fileText: String)}",
      duplicatedHighlights.isEmpty
    )
  }

  protected def configureByText(text: String): TestPrepareResult = {
    val fileText = createTestText(text).withNormalizedSeparator.trim

    val (_, expectedRanges) =
      MarkersUtils.extractMarker(fileText, START, END)

    val expectedHighlights =
      expectedRanges.map(ExpectedHighlight)

    val (normalizedText, offset) =
      findCaretOffset(fileText, stripTrailingSpaces = true)

    if (isScratchFile) {
      val vFile = createScratchFile(normalizedText)
      myFixture.configureFromExistingVirtualFile(vFile)
    } else {
      myFixture.configureByText(fileType, normalizedText)
    }

    onFileCreated(myFixture.getFile)

    val actualHighlights =
      myFixture.doHighlighting().asScala
        .filter(highlightInfo => descriptionMatches(highlightInfo.getDescription))
        .filter(checkOffset(_, offset))

    TestPrepareResult(myFixture.getFile.getText, expectedHighlights, actualHighlights.toSeq)
  }

  private def createScratchFile(normalizedText: String) = {
    val fileName = s"aaa.${fileType.getDefaultExtension}"
    val language = fileType.getLanguage
    ScratchRootType.getInstance.createScratchFile(getProject, fileName, language, normalizedText)
  }

  protected def onFileCreated(file: PsiFile): Unit = ()

  protected def createTestText(text: String): String = text
}

object ScalaHighlightsTestBase {

  case class ExpectedHighlight(range: TextRange)
  case class TestPrepareResult(fileText: String, expectedHighlights: Seq[ExpectedHighlight], actualHighlights: Seq[HighlightInfo])

  private def highlightedRange(info: HighlightInfo): TextRange =
    new TextRange(info.getStartOffset, info.getEndOffset)

  private def checkOffset(highlightInfo: HighlightInfo, offset: Int): Boolean =
    if (offset == -1) {
      true
    } else {
      val range = highlightedRange(highlightInfo)
      range.containsOffset(offset)
    }
}