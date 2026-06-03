package org.jetbrains.plugins.scala.codeInspection

import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.fileTypes.LanguageFileType
import com.intellij.psi.PsiFile
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestCase
import org.jetbrains.plugins.scala.codeInspection.ScalaQuickFixTestFixture.{ExpectedHighlight, TestPrepareResult}
import org.jetbrains.plugins.scala.extensions.{HighlightInfoExt, StringExt}
import org.jetbrains.plugins.scala.{EditorTests, ScalaFileType}
import org.junit.Assert.assertTrue
import org.junit.experimental.categories.Category

/**
 * This is a base test to test quick fixes.
 * It simply delegates all implementations to [[ScalaQuickFixTestFixture]].
 * If you want to use a different base test class, use [[ScalaQuickFixTestFixture]] directly
 */
@Category(Array(classOf[EditorTests]))
abstract class ScalaAnnotatorQuickFixTestBase extends ScalaLightCodeInsightFixtureTestCase {

  private var scalaQuickFixFixture: ScalaQuickFixTestFixture = _

  override protected def setUp(): Unit = {
    super.setUp()

    scalaQuickFixFixture = new ScalaQuickFixTestFixture(
      myFixture,
      description,
      fileType,
      isScratchFile = isScratchFile,
      shouldPass = shouldPass,
      onFileCreated = onFileCreated,
      createTestText = createTestText,
      trimExpectedText = trimExpectedText
    )

    scalaQuickFixFixture.descriptionMatcher = descriptionMatches
  }

  protected def description: String
  protected def fileType: LanguageFileType = ScalaFileType.INSTANCE
  protected def isScratchFile: Boolean = false
  protected def descriptionMatches(s: String): Boolean = s == description.withNormalizedSeparator.trim
  protected def onFileCreated(file: PsiFile): Unit = ()
  protected def createTestText(text: String): String = text
  protected def trimExpectedText: Boolean = true

  protected override def checkTextHasNoErrors(text: String): Unit = {
    configureByText(text)
    val highlights = findMatchingHighlightings(text)

    val ranges = highlights.map(_.range)

    def rangeText = ranges.mkString(", ")

    assertTrue(
      if (shouldPass) s"Highlights found at: $rangeText:\n${scalaQuickFixFixture.highlightsDebugText(highlights, getFile.getText)}"
      else failingPassed,
      !shouldPass ^ ranges.isEmpty
    )
  }

  protected def configureByText(text: String): Seq[ExpectedHighlight] =
    scalaQuickFixFixture.configureByText(text)

  protected def findMatchingHighlightings(text: String): Seq[HighlightInfo] =
    scalaQuickFixFixture.findMatchingHighlights(text)

  protected def testQuickFix(text: String, expected: String, hint: String): Unit =
    scalaQuickFixFixture.testQuickFix(text, expected, hint)

  protected def testQuickFixes(text: String, expected: String, hint: String): Unit =
    scalaQuickFixFixture.testQuickFixes(text, expected, hint)

  protected def testQuickFixAllInFile(text: String, expected: String, hint: String): Unit =
    scalaQuickFixFixture.testQuickFixAllInFile(text, expected, hint)

  protected def testQuickFixAllInFile(text: String, expected: String, hints: Seq[String]): Unit =
    scalaQuickFixFixture.testQuickFixAllInFile(text, expected, hints)

  protected def checkNotFixable(text: String, hint: String): Unit =
    scalaQuickFixFixture.checkNotFixable(text, hint)

  protected def checkNotFixable(text: String, hintFilter: String => Boolean): Unit =
    scalaQuickFixFixture.checkNotFixable(text, hintFilter)

  protected def checkIsNotAvailable(text: String, hint: String): Unit =
    scalaQuickFixFixture.checkIsNotAvailable(text, hint)

  protected def doFindQuickFixes(text: String, hint: String, failOnEmptyErrors: Boolean = true): Seq[IntentionAction] =
    scalaQuickFixFixture.doFindQuickFixes(text, hint, failOnEmptyErrors)

  protected def doFindQuickFixes(text: String, hints: Seq[String], failOnEmptyErrors: Boolean): Seq[IntentionAction] =
    scalaQuickFixFixture.doFindQuickFixes(text, hints, failOnEmptyErrors)

  protected def findAllQuickFixes(text: String, failOnEmptyErrors: Boolean = true): Seq[IntentionAction] =
    scalaQuickFixFixture.findAllQuickFixes(text, failOnEmptyErrors)

  protected def checkTextHasError(text: String, allowAdditionalHighlights: Boolean = false): Unit =
    scalaQuickFixFixture.checkTextHasError(text, allowAdditionalHighlights)

  protected def assertTextHasError(
    expectedHighlights: Seq[ExpectedHighlight],
    actualHighlights: Seq[HighlightInfo],
    allowAdditionalHighlights: Boolean,
  ): Unit =
    scalaQuickFixFixture.assertTextHasError(
      expectedHighlights,
      actualHighlights,
      allowAdditionalHighlights,
    )
}