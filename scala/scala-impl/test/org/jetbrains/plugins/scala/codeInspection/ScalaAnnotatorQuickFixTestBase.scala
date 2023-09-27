package org.jetbrains.plugins.scala.codeInspection

import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.ide.scratch.ScratchRootType
import com.intellij.openapi.fileTypes.LanguageFileType
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestCase
import org.jetbrains.plugins.scala.codeInspection.ScalaAnnotatorQuickFixTestBase.{ExpectedHighlight, TestPrepareResult, checkOffset}
import org.jetbrains.plugins.scala.extensions.{HighlightInfoExt, ObjectExt, StringExt, executeWriteActionCommand}
import org.jetbrains.plugins.scala.util.MarkersUtils
import org.jetbrains.plugins.scala.{EditorTests, ScalaFileType}
import org.junit.Assert.{assertFalse, assertTrue, fail}
import org.junit.experimental.categories.Category

import scala.jdk.CollectionConverters.CollectionHasAsScala

@Category(Array(classOf[EditorTests]))
abstract class ScalaAnnotatorQuickFixTestBase extends ScalaLightCodeInsightFixtureTestCase {

  import ScalaAnnotatorQuickFixTestBase.quickFixes

  protected def testQuickFix(text: String, expected: String, hint: String, trimExpectedText: Boolean = true): Unit = {
    val action = doFindQuickFix(text, hint)

    executeWriteActionCommand() {
      action.invoke(getProject, getEditor, getFile)
    }(getProject)

    val expectedFileText = createTestText(expected)
    myFixture.checkResult(expectedFileText.withNormalizedSeparator.pipeIf(trimExpectedText)(_.trim), true)
  }

  protected def testQuickFixAllInFile(text: String, expected: String, hint: String): Unit = {
    val actions = doFindQuickFixes(text, hint)

    executeWriteActionCommand() {
      actions.foreach(_.invoke(getProject, getEditor, getFile))
    }(getProject)

    val expectedFileText = createTestText(expected)
    myFixture.checkResult(expectedFileText.withNormalizedSeparator.trim, true)
  }

  protected def checkNotFixable(text: String, hint: String): Unit = {
    checkNotFixable(text, _ == hint)
  }

  protected def checkNotFixable(text: String, hintFilter: String => Boolean): Unit = {
    val maybeAction = findQuickFix(text, hintFilter)
    assertTrue("Quick fix found.", maybeAction.isEmpty)
  }

  protected def checkIsNotAvailable(text: String, hint: String): Unit = {
    val action = doFindQuickFix(text, hint)
    assertFalse("Quick fix is available", action.isAvailable(getProject, getEditor, getFile))
  }

  private def findQuickFix(text: String, hintFilter: String => Boolean): Option[IntentionAction] = {
    val actions = findAllQuickFixes(text, failOnEmptyErrors = false)
    actions.find(a => hintFilter(a.getText))
  }

  private def doFindQuickFix(text: String, hint: String, failOnEmptyErrors: Boolean = true): IntentionAction =
    doFindQuickFixes(text, hint, failOnEmptyErrors).head

  protected def doFindQuickFixes(text: String, hint: String, failOnEmptyErrors: Boolean = true): Seq[IntentionAction] = {
    val actions = findAllQuickFixes(text, failOnEmptyErrors)
    val actionsMatching = actions.filter(_.getText == hint)
    assert(actionsMatching.nonEmpty, s"Quick fixes not found. Available actions:\n${actions.map(_.getText).mkString("\n")}")
    actionsMatching
  }

  protected def findAllQuickFixes(text: String, failOnEmptyErrors: Boolean = true): Seq[IntentionAction] = {
    val highlights = configureByText(text).actualHighlights
    if (highlights.isEmpty && failOnEmptyErrors) {
      fail("Errors not found.").asInstanceOf[Nothing]
    }
    else {
      highlights.flatMap(quickFixes)
    }
  }

  protected def description: String

  protected def fileType: LanguageFileType = ScalaFileType.INSTANCE
  protected def isScratchFile: Boolean = false

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
    val fileTextNormalized =
      createTestText(text).withNormalizedSeparator.trim

    if (isScratchFile) {
      val vFile = createScratchFile(fileTextNormalized)
      myFixture.configureFromExistingVirtualFile(vFile)
    } else {
      myFixture.configureByText(fileType, fileTextNormalized)
    }

    val (_, expectedRanges) = MarkersUtils.extractMarker(fileTextNormalized, START, END, caretMarker = Some(CARET))
    val expectedHighlights = expectedRanges.map(ExpectedHighlight)

    //we calculate caret offset after the editor was configured and all selection markers are stripped away
    val explicitCaretMarkerOffset =
      if (fileTextNormalized.contains(CARET))
        getEditor.getCaretModel.getOffset
      else
        -1

    onFileCreated(myFixture.getFile)

    val highlightsAll =
      myFixture.doHighlighting().asScala.toSeq
    val highlightsFiltered =
      highlightsAll
        .filter(highlightInfo => {
          val description = highlightInfo.getDescription
          description != null && descriptionMatches(description)
        })
        .filter(checkOffset(_, explicitCaretMarkerOffset))

    TestPrepareResult(myFixture.getFile.getText, expectedHighlights, highlightsFiltered)
  }

  private def createScratchFile(normalizedText: String) = {
    val fileName = s"aaa.${fileType.getDefaultExtension}"
    val language = fileType.getLanguage
    ScratchRootType.getInstance.createScratchFile(getProject, fileName, language, normalizedText)
  }

  protected def onFileCreated(file: PsiFile): Unit = ()

  protected def createTestText(text: String): String = text
}

object ScalaAnnotatorQuickFixTestBase {
  case class ExpectedHighlight(range: TextRange)
  case class TestPrepareResult(fileText: String, expectedHighlights: Seq[ExpectedHighlight], actualHighlights: Seq[HighlightInfo])

  private def quickFixes(info: HighlightInfo): Seq[IntentionAction] = {
    val builder = Seq.newBuilder[IntentionAction]
    info.findRegisteredQuickFix { (descriptor, _) =>
      builder += descriptor.getAction
      null
    }
    builder.result()
  }

  private def highlightedRange(info: HighlightInfo): TextRange =
    new TextRange(info.getStartOffset, info.getEndOffset)

  private def checkOffset(highlightInfo: HighlightInfo, caretOffset: Int): Boolean =
    if (caretOffset == -1)
      true
    else {
      val range = highlightedRange(highlightInfo)
      range.containsOffset(caretOffset)
    }
}
