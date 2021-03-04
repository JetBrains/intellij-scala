package org.jetbrains.plugins.scala
package codeInspection

import com.intellij.codeHighlighting.HighlightDisplayLevel
import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInspection.ex.ScopeToolState
import com.intellij.codeInspection.{LocalInspectionEP, LocalInspectionTool}
import com.intellij.ide.scratch.ScratchRootType
import com.intellij.openapi.fileTypes.LanguageFileType
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.registry.Registry
import com.intellij.profile.codeInspection.ProjectInspectionProfileManager
import com.intellij.psi.PsiFile
import org.jetbrains.plugins.scala.base.{ScalaLightCodeInsightFixtureTestAdapter, ScalaSdkOwner}
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestAdapter.{findCaretOffset, normalize}
import org.jetbrains.plugins.scala.extensions.{HighlightInfoExt, executeWriteActionCommand}
import org.jetbrains.plugins.scala.externalHighlighters.ScalaHighlightingMode
import org.jetbrains.plugins.scala.util.MarkersUtils
import org.junit.Assert._

import scala.jdk.CollectionConverters._

abstract class ScalaHighlightsTestBase extends ScalaLightCodeInsightFixtureTestAdapter {
  self: ScalaLightCodeInsightFixtureTestAdapter =>

  import ScalaHighlightsTestBase._

  protected val description: String

  protected val fileType: LanguageFileType = ScalaFileType.INSTANCE
  protected val isScratchFile: Boolean = false

  override protected def setUp(): Unit = {
    super.setUp()
    Registry.get(ScalaHighlightingMode.ShowDotcErrorsKey).setValue(false, getTestRootDisposable)
  }

  protected def descriptionMatches(s: String): Boolean = s == normalize(description)

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
    val fileText = normalize(createTestText(text))

    val (_, expectedRanges) =
      MarkersUtils.extractSequentialMarkers(fileText, START, END)

    val expectedHighlights =
      expectedRanges.map(ExpectedHighlight)

    val (normalizedText, offset) =
      findCaretOffset(fileText, stripTrailingSpaces = true)

    val fixture = getFixture
    if (isScratchFile) {
      val vFile = createScratchFile(normalizedText)
      fixture.configureFromExistingVirtualFile(vFile)
    } else {
      fixture.configureByText(fileType, normalizedText)
    }
    onFileCreated(fixture.getFile)

    val actualHighlights =
      fixture.doHighlighting().asScala
        .filter(it => descriptionMatches(it.getDescription))
        .filter(checkOffset(_, offset))

    TestPrepareResult(fixture.getFile.getText, expectedHighlights, actualHighlights.toSeq)
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

abstract class ScalaQuickFixTestBase extends ScalaInspectionTestBase

abstract class ScalaAnnotatorQuickFixTestBase extends ScalaHighlightsTestBase {
  import ScalaAnnotatorQuickFixTestBase.quickFixes

  protected def testQuickFix(text: String, expected: String, hint: String): Unit = {
    val action = doFindQuickFix(text, hint)

    executeWriteActionCommand() {
      action.invoke(getProject, getEditor, getFile)
    }(getProject)

    val expectedFileText = createTestText(expected)
    getFixture.checkResult(normalize(expectedFileText), /*stripTrailingSpaces = */ true)
  }

  protected def checkNotFixable(text: String, hint: String): Unit = {
    val maybeAction = findQuickFix(text, hint, failOnEmptyErrors = false)
    assertTrue("Quick fix found.", maybeAction.isEmpty)
  }

  protected def checkIsNotAvailable(text: String, hint: String): Unit = {
    val action = doFindQuickFix(text, hint)
    assertFalse("Quick fix is available", action.isAvailable(getProject, getEditor, getFile))
  }

  private def findQuickFix(text: String, hint: String, failOnEmptyErrors: Boolean = true): Option[IntentionAction] = {
    val actions = findAllQuickFixes(text, failOnEmptyErrors)
    actions.find(_.getText == hint)
  }

  private def doFindQuickFix(text: String, hint: String, failOnEmptyErrors: Boolean = true): IntentionAction = {
    val actions = findAllQuickFixes(text, failOnEmptyErrors)
    val action = actions.find(_.getText == hint).getOrElse(
      fail(s"Quick fix not found. Available actions:\n${actions.map(_.getText).mkString("\n")}").asInstanceOf[Nothing]
    )
    action
  }

  private def findAllQuickFixes(text: String, failOnEmptyErrors: Boolean = true): Seq[IntentionAction] =
    configureByText(text).actualHighlights match {
      case Seq() if failOnEmptyErrors => fail("Errors not found.").asInstanceOf[Nothing]
      case seq                        => seq.flatMap(quickFixes)
    }
}

object ScalaAnnotatorQuickFixTestBase {
  private def quickFixes(info: HighlightInfo): Seq[IntentionAction] = {
    Option(info.quickFixActionRanges).toSeq
      .flatMap(_.asScala)
      .flatMap(pair => Option(pair))
      .map(_.getFirst.getAction)
  }
}

abstract class ScalaInspectionTestBase extends ScalaAnnotatorQuickFixTestBase {
  override protected def defaultVersionOverride: Option[ScalaVersion] = Some(ScalaSdkOwner.preferableSdkVersion)

  protected val classOfInspection: Class[_ <: LocalInspectionTool]

  protected override def setUp(): Unit = {
    super.setUp()
    getFixture.enableInspections(classOfInspection)
  }
}

trait ForceInspectionSeverity extends ScalaInspectionTestBase {

  private var oldLevel: HighlightDisplayLevel = _
  protected override def setUp(): Unit = {
    super.setUp()
    val toolState = inspectionToolState
    oldLevel = toolState.getLevel
    toolState.setLevel(forcedInspectionSeverity)
  }

  override def tearDown(): Unit = {
    inspectionToolState.setLevel(oldLevel)
    super.tearDown()
  }

  private def inspectionToolState: ScopeToolState = {
    val profile = ProjectInspectionProfileManager.getInstance(getFixture.getProject).getCurrentProfile
    profile.getToolDefaultState(inspectionEP.getShortName, getFixture.getProject)
  }

  private def inspectionEP =
    LocalInspectionEP.LOCAL_INSPECTION
      .getExtensions
      .find(_.implementationClass == classOfInspection.getCanonicalName)
      .get

  protected def forcedInspectionSeverity: HighlightDisplayLevel
}