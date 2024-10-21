

package org.jetbrains.plugins.scala.codeInspection

import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.ide.scratch.ScratchRootType
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileTypes.LanguageFileType
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile
import com.intellij.testFramework.EditorTestUtil
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import org.jetbrains.plugins.scala.ScalaFileType
import org.jetbrains.plugins.scala.codeInspection.ScalaQuickFixTestFixture.{ExpectedHighlight, TestPrepareResult, checkOffset, findRegisteredQuickFixes}
import org.jetbrains.plugins.scala.extensions.{HighlightInfoExt, NonNullObjectExt, StringExt, executeWriteActionCommand}
import org.jetbrains.plugins.scala.util.MarkersUtils
import org.junit.Assert.{assertFalse, assertTrue, fail}

import scala.annotation.nowarn
import scala.jdk.CollectionConverters.CollectionHasAsScala

/**
 * @param description this is only used in assertion errors, for actual comparison logic see [[descriptionMatcher]]
 *                    It is lazy (by-name parameter) for historical reasons.
 *                    Some inheritors of ScalaAnnotatorQuickFixTestBase don't implement this method and jut use `???`
 * @param shouldPass whether the test should pass<br>.
 *                   Avoid using this parameter, it's left for legacy tests.
 *                   Instead, specify explicitly what is the expected (even failed) result
 */
final class ScalaQuickFixTestFixture(
  baseFixture: CodeInsightTestFixture,
  description: => String,
  fileType: LanguageFileType = ScalaFileType.INSTANCE,
  isScratchFile: Boolean = false,
  @deprecated shouldPass: Boolean = true,
  onFileCreated: PsiFile => Unit = _ => (),
  createTestText: String => String = identity
) {

  var descriptionMatcher: String => Boolean = _ == description.withNormalizedSeparator.trim

  private def getProject: Project = baseFixture.getProject
  private def getEditor: Editor = baseFixture.getEditor
  private def getFile: PsiFile = baseFixture.getFile

  protected val failingPassed: String = "Test has passed, but was supposed to fail"

  protected val START: String = EditorTestUtil.SELECTION_START_TAG
  protected val END: String = EditorTestUtil.SELECTION_END_TAG
  protected val CARET: String = EditorTestUtil.CARET_TAG

  def testQuickFix(text: String, expected: String, hint: String, trimExpectedText: Boolean = true): Unit = {
    val action = doFindQuickFix(text, hint)
    applyQuickFixesAndCheckExpected(Seq(action), expected, trimExpectedText)
  }

  def testQuickFixes(text: String, expected: String, hint: String, trimExpectedText: Boolean = true): Unit = {
    val actions: Seq[IntentionAction] = doFindQuickFixes(text, hint)
    applyQuickFixesAndCheckExpected(actions, expected, trimExpectedText)
  }

  def testQuickFixAllInFile(text: String, expected: String, hint: String): Unit =
    testQuickFixAllInFile(text, expected, Seq(hint))

  def testQuickFixAllInFile(text: String, expected: String, hints: Seq[String]): Unit = {
    val actions = doFindQuickFixes(text, hints, failOnEmptyErrors = true)
    applyQuickFixesAndCheckExpected(actions, expected)
  }

  def applyQuickFixesAndCheckExpected(
    actions: Seq[IntentionAction],
    expected: String,
    trimExpectedText: Boolean = true
  ): Unit = {
    executeWriteActionCommand() {
      actions.foreach(_.invoke(getProject, getEditor, getFile))
    }(getProject)

    val expectedFileText = createTestText(expected)
    val expectedFileTextProcessed = expectedFileText.withNormalizedSeparator.pipeIf(trimExpectedText)(_.trim)
    baseFixture.checkResult(expectedFileTextProcessed, true)
  }

  def checkNotFixable(text: String, hint: String): Unit = {
    checkNotFixable(text, _ == hint)
  }

  def checkNotFixable(text: String, hintFilter: String => Boolean): Unit = {
    val maybeAction = findQuickFix(text, hintFilter)
    assertTrue("Quick fix found.", maybeAction.isEmpty)
  }

  def checkIsNotAvailable(text: String, hint: String): Unit = {
    val action = doFindQuickFix(text, hint)
    assertFalse("Quick fix is available", action.isAvailable(getProject, getEditor, getFile))
  }

  private def findQuickFix(text: String, hintFilter: String => Boolean): Option[IntentionAction] = {
    val actions = findAllQuickFixes(text, failOnEmptyErrors = false)
    actions.find(a => hintFilter(a.getText))
  }

  private def doFindQuickFix(text: String, hint: String, failOnEmptyErrors: Boolean = true): IntentionAction =
    doFindQuickFixes(text, hint, failOnEmptyErrors).head

  def doFindQuickFixes(text: String, hint: String, failOnEmptyErrors: Boolean = true): Seq[IntentionAction] =
    doFindQuickFixes(text, Seq(hint), failOnEmptyErrors)

  def doFindQuickFixes(text: String, hints: Seq[String], failOnEmptyErrors: Boolean): Seq[IntentionAction] = {
    val actions = findAllQuickFixes(text, failOnEmptyErrors)
    val hintSet = hints.toSet
    val actionsMatching = actions.filter(a => hintSet.contains(a.getText))
    assert(actionsMatching.nonEmpty,
      s"""Quick fixes not found.
         |Expected actions:
         |  ${hints.mkString("  \n")}
         |Available actions:
         |  ${actions.map(_.getText).mkString("  \n")}""".stripMargin
    )
    actionsMatching
  }

  def findAllQuickFixes(text: String, failOnEmptyErrors: Boolean = true): Seq[IntentionAction] = {
    configureByText(text)

    val highlights = findMatchingHighlights(text)
    if (highlights.isEmpty && failOnEmptyErrors) {
      fail("Errors not found.").asInstanceOf[Nothing]
    }
    else {
      highlights.flatMap(findRegisteredQuickFixes)
    }
  }

  def highlightsDebugText(highlights: Seq[HighlightInfo], fileText: String): String = {
    val strings = highlights.map(highlightsDebugText(_, fileText))
    val indent = "  "
    strings.mkString(indent, indent + "\n", "")
  }

  def highlightsDebugText(info: HighlightInfo, fileText: String): String = {
    val range = info.range
    val rangeText = fileText.substring(range.getStartOffset, range.getEndOffset)
    s"$range[$rangeText]: ${info.getDescription}"
  }

  def checkTextHasError(text: String, allowAdditionalHighlights: Boolean = false): Unit = {
    val expectedHighlights = configureByText(text)
    val actualHighlights = findMatchingHighlights(text)
    assertTextHasError(expectedHighlights, actualHighlights, allowAdditionalHighlights)
  }

  def assertTextHasError(
    expectedHighlights: Seq[ExpectedHighlight],
    actualHighlights: Seq[HighlightInfo],
    allowAdditionalHighlights: Boolean,
  ): Unit = {
    val expectedHighlightRanges = expectedHighlights.map(_.range)
    val actualHighlightRanges = actualHighlights.map(_.range)

    val expectedRangesNotFound = expectedHighlightRanges.filterNot(actualHighlightRanges.contains)
    if (shouldPass: @nowarn("cat=deprecation")) {
      assertTrue(
        s"Highlights not found: $description",
        actualHighlightRanges.nonEmpty
      )
      assertTrue(
        s"""Highlights found at: ${actualHighlightRanges.mkString(", ")}
           |not found: ${expectedRangesNotFound.mkString(", ")}""".stripMargin,
        expectedRangesNotFound.isEmpty
      )

      val fileText = getFile.getText
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

  def configureByText(text: String): Seq[ExpectedHighlight] = {
    val fileTextNormalized =
      createTestText(text).withNormalizedSeparator.trim

    if (isScratchFile) {
      val vFile = createScratchFile(fileTextNormalized)
      baseFixture.configureFromExistingVirtualFile(vFile)
    } else {
      baseFixture.configureByText(fileType, fileTextNormalized)
    }

    val (_, expectedRanges) = MarkersUtils.extractMarker(fileTextNormalized, START, END, caretMarker = Some(CARET))
    val expectedHighlights = expectedRanges.map(ExpectedHighlight)

    onFileCreated(baseFixture.getFile)
    expectedHighlights
  }

  /**
   * @param text the original text is only used to check if there is an explicit caret marker inside it.
   *             If there is a caret marker, only highlightings at caret are checked.
   */
  def findMatchingHighlights(text: String): Seq[HighlightInfo] = {
    val caretOffset = if (text.contains(CARET)) Some(getEditor.getCaretModel.getOffset) else None
    findMatchingHighlights(caretOffset)
  }

  def findMatchingHighlights(caretOffset: Option[Int] = None): Seq[HighlightInfo] = {
    val highlightsAll = baseFixture.doHighlighting().asScala.toSeq
    val highlightsMatchingDescription = highlightsAll.filter(highlightInfo => {
      val description = highlightInfo.getDescription
      description != null && descriptionMatcher(description)
    })
    val highlightsInRange = highlightsMatchingDescription.filter(checkOffset(_, caretOffset))
    highlightsInRange
  }

  private def createScratchFile(normalizedText: String) = {
    val fileName = s"aaa.${fileType.getDefaultExtension}"
    val language = fileType.getLanguage
    ScratchRootType.getInstance.createScratchFile(getProject, fileName, language, normalizedText)
  }
}

object ScalaQuickFixTestFixture {
  case class ExpectedHighlight(range: TextRange)
  case class TestPrepareResult(expectedHighlights: Seq[ExpectedHighlight], actualHighlights: Seq[HighlightInfo])

  def findRegisteredQuickFixes(info: HighlightInfo): Seq[IntentionAction] = {
    val builder = Seq.newBuilder[IntentionAction]
    info.findRegisteredQuickFix { (descriptor, _) =>
      builder += descriptor.getAction
      null
    }
    builder.result()
  }

  private def highlightedRange(info: HighlightInfo): TextRange =
    new TextRange(info.getStartOffset, info.getEndOffset)

  private def checkOffset(highlightInfo: HighlightInfo, caretOffset: Option[Int]): Boolean = {
    caretOffset.forall { offset =>
      val range = highlightedRange(highlightInfo)
      range.containsOffset(offset)
    }
  }
}
