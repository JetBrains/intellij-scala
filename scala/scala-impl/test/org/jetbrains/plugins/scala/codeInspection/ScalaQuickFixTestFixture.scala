

package org.jetbrains.plugins.scala.codeInspection

import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.ide.scratch.ScratchRootType
import com.intellij.lang.impl.modcommand.ModCommandActionWrapper
import com.intellij.modcommand.ActionContext
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileTypes.LanguageFileType
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile
import com.intellij.testFramework.EditorTestUtil
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import org.jetbrains.plugins.scala.ScalaFileType
import org.jetbrains.plugins.scala.codeInspection.ScalaQuickFixTestFixture.{ExpectedHighlight, doesHighlightingErrorRangeOrQuickFixRangeIncludeCaret, findRegisteredQuickFixes}
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
  createTestText: String => String = identity,
  trimExpectedText: Boolean = true
) {

  var descriptionMatcher: String => Boolean = _ == description.withNormalizedSeparator.trim

  private def getProject: Project = baseFixture.getProject
  private def getEditor: Editor = baseFixture.getEditor
  private def getFile: PsiFile = baseFixture.getFile

  protected val failingPassed: String = "Test has passed, but was supposed to fail"

  protected val START: String = EditorTestUtil.SELECTION_START_TAG
  protected val END: String = EditorTestUtil.SELECTION_END_TAG
  protected val CARET: String = EditorTestUtil.CARET_TAG

  def testQuickFix(text: String, expected: String, hint: String): Unit = {
    val action = doFindQuickFix(text, hint)
    applyQuickFixesAndCheckExpected(Seq(action), expected)
  }

  def testQuickFixes(text: String, expected: String, hint: String): Unit = {
    val actions: Seq[IntentionAction] = doFindQuickFixes(text, hint)
    applyQuickFixesAndCheckExpected(actions, expected)
  }

  def testQuickFixAllInFile(text: String, expected: String, hint: String): Unit =
    testQuickFixAllInFile(text, expected, Seq(hint))

  def testQuickFixAllInFile(text: String, expected: String, hints: Seq[String]): Unit = {
    val actions = doFindQuickFixes(text, hints, failOnEmptyErrors = true)
    applyQuickFixesAndCheckExpected(actions, expected)
  }

  def applyQuickFixesAndCheckExpected(
    actions: Seq[IntentionAction],
    expected: String
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

  def getPresentableText(action: IntentionAction): String = action match {
    case wrapper: ModCommandActionWrapper =>
      val context = ActionContext.from(getEditor, getFile)
      val presentation = wrapper.asModCommandAction().getPresentation(context)
      Option(presentation).fold(action.getText)(_.name())
    case _ => action.getText
  }

  private def findQuickFix(text: String, hintFilter: String => Boolean): Option[IntentionAction] = {
    val actions = findAllQuickFixes(text, failOnEmptyErrors = false)
    actions.find(a => hintFilter(getPresentableText(a)))
  }

  private def doFindQuickFix(text: String, hint: String, failOnEmptyErrors: Boolean = true): IntentionAction =
    doFindQuickFixes(text, hint, failOnEmptyErrors).head

  def doFindQuickFixes(text: String, hint: String, failOnEmptyErrors: Boolean = true): Seq[IntentionAction] =
    doFindQuickFixes(text, Seq(hint), failOnEmptyErrors)

  def doFindQuickFixes(text: String, hints: Seq[String], failOnEmptyErrors: Boolean): Seq[IntentionAction] = {
    val actions = findAllQuickFixes(text, failOnEmptyErrors)
    val hintSet = hints.toSet
    val actionsMatching = actions.filter(a => hintSet.contains(getPresentableText(a)))
    assert(actionsMatching.nonEmpty,
      s"""Quick fixes not found.
         |Expected actions:
         |${hints.mkString("\n").indent(2)}
         |Available actions:
         |${actions.map(getPresentableText).mkString("\n").indent(2)}""".stripMargin
    )
    actionsMatching
  }

  def findAllQuickFixes(text: String, failOnEmptyErrors: Boolean = true): Seq[IntentionAction] = {
    configureByText(text)

    val highlights = findMatchingHighlights(text)
    if (highlights.matching.isEmpty && failOnEmptyErrors) {
      val errorMessage = buildErrorMessage(text, highlights)
      fail(errorMessage).asInstanceOf[Nothing]
    }
    else {
      highlights.matching.flatMap(findRegisteredQuickFixes)
    }
  }

  private def buildErrorMessage(text: String, highlights: MatchingHighlightInfos): String = {
    val result = new StringBuilder()
    result.append("Matching errors not found.")

    if (text.contains(CARET)) {
      result.append(s"\nCaret offset: ${getEditor.getCaretModel.getOffset}")
    }

    if (highlights.matchingDescriptionOnly.nonEmpty) {
      result.append(
        s"""\nMatching descriptions in other locations:
           |${highlights.matchingDescriptionOnly.mkString("\n").indent(2)})""".stripMargin
      )
    }

    result.toString()
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
    assertTextHasError(expectedHighlights, actualHighlights.matching, allowAdditionalHighlights)
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
  def findMatchingHighlights(text: String): MatchingHighlightInfos = {
    val caretOffset = if (text.contains(CARET)) Some(getEditor.getCaretModel.getOffset) else None
    findMatchingHighlights(caretOffset)
  }

  case class MatchingHighlightInfos(
    matching: Seq[HighlightInfo],
    matchingDescriptionOnly: Seq[HighlightInfo],
    all: Seq[HighlightInfo]
  )

  def findMatchingHighlights(caretOffset: Option[Int] = None): MatchingHighlightInfos = {
    val highlightsAll = baseFixture.doHighlighting().asScala.toSeq
    val highlightsMatchingDescription = highlightsAll.filter(highlightInfo => {
      val description = highlightInfo.getDescription
      description != null && descriptionMatcher(description)
    })
    val highlightsInHighlightOrQuickFixRange = highlightsMatchingDescription.filter(doesHighlightingErrorRangeOrQuickFixRangeIncludeCaret(_, caretOffset))
    MatchingHighlightInfos(
      highlightsInHighlightOrQuickFixRange,
      highlightsMatchingDescription,
      highlightsAll
    )
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

  private def doesHighlightingErrorRangeOrQuickFixRangeIncludeCaret(highlightInfo: HighlightInfo, caretOffset: Option[Int]): Boolean = {
    caretOffset.forall(doesHighlightingErrorRangeOrQuickFixRangeIncludeCaret(highlightInfo, _))
  }

  /**
   * Some errors have quick fixes which are located in range different from the highlighting error range.
   * In the tests the caret marker currently kinda can represent both, for convenience.
   * I do realize that in some more narrow tests this might be a problem. In this case we would need to split the test functionality
   *
   * Examples:
   *  - See SCL-25481. The "Type mismatch" error is shown after `}` (as expected) but the F2 (Go To Next Error) and the quick fix are on "42"
   *    {{{
   *      def foo2: Int = {
   *        "42"
   *      }
   *    }}}
   */
  //noinspection ApiStatus
  private def doesHighlightingErrorRangeOrQuickFixRangeIncludeCaret(highlightInfo: HighlightInfo, caretOffset: Int): Boolean = {
    val range = highlightedRange(highlightInfo)
    val caretIsInHighlightingRange = range.containsOffset(caretOffset)

    // NOTE: the API of `findRegisteredQuickFix` is peculiar - it does not return Boolean but rather with some non-null value,
    // So we use Object return value instead of just `boolean` (but it could be any marker non-null object, even a String)
    // For us here the main important thing is to check if the range matches
    val matchingByQuickFixRange: java.lang.Boolean = highlightInfo.findRegisteredQuickFix((descriptor, _) => {
      // Note: filtering by the quick fix test / hint is done on later stages, here we only filter by the range
      val caretIsInFixRange = descriptor.getFixRange.containsOffset(caretOffset)
      if (caretIsInFixRange)
        java.lang.Boolean.TRUE
      else
        null
    })
    val caretIsInQuickFixRange = matchingByQuickFixRange != null
    caretIsInHighlightingRange || caretIsInQuickFixRange
  }
}
