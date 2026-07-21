package org.jetbrains.plugins.scala.lang.navigation

import com.intellij.codeInsight.TargetElementUtil
import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.LineMarkerInfo.LineMarkerGutterIconRenderer
import com.intellij.icons.AllIcons
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.psi.{PsiDocumentManager, PsiElement}
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import org.jetbrains.plugins.scala.annotator.gutter.LineMarkerInfoPresentationUtils
import org.jetbrains.plugins.scala.lang.actions.editor.enter.scala3.TestIndentUtils
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.ScExportStmt
import org.junit.Assert.{assertNotNull, fail}

import java.awt.event.MouseEvent
import javax.swing.JLabel
import scala.jdk.CollectionConverters.ListHasAsScala

/**
 * Test fixture that performs end-to-end navigation through "override/implements" gutter icons
 * and returns the PSI target opened by that navigation.
 *
 * How it works:
 *  1. Reads all gutter markers at caret from the original `CodeInsightTestFixture`
 *  1. Filters only super-member markers (`OverridingMethod` / `ImplementingMethod`) and asserts that there is exactly one matching marker
 *  1. Invokes the marker navigation handler with a synthetic click event
 *  1. Waits for IDE queue processing (`PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()`)
 *  1. Resolves the selected editor/file and extracts the target element at caret
 *
 * Related gutter-icon tests in this repository:
 *  - [[org.jetbrains.plugins.scala.annotator.gutter.GutterMarkersTest]]<br>
 *    (for example, `testMemberHasImplementations`, `testMemberHasOverrides`, `testMergedOverridingMarks`)
 *  - [[org.jetbrains.plugins.scala.annotator.gutter.SAMGutterMarkersTest_2_13]]<br>
 *    (SAM "Implements member" gutter tooltip coverage)
 *
 * How this is different from the existing gutter tests:
 *  - Most existing gutter tests assert marker presence, icon/tooltip text, or marker count at caret
 *  - This fixture specifically executes the marker action itself and verifies the navigation result
 *    by following the editor switch and resolving the opened target PSI element
 *  - It therefore covers interaction-level behavior (click -> async dispatch -> opened target),
 *    not just marker rendering/availability
 */
final class SuperMemberGutterNavigationFixture(private val originalFixture: CodeInsightTestFixture) {

  private val project = originalFixture.getProject

  def navigateToSuperMemberTarget(fileName: String): PsiElement =
    navigateToTarget(fileName, isSuperNavigationLineMarker)

  def navigateToImplementationTarget(fileName: String): PsiElement =
    navigateToTarget(fileName, isImplementationNavigationLineMarker)

  private def navigateToTarget(fileName: String, markerFilter: LineMarkerInfo[_] => Boolean): PsiElement = {
    val lineMarkerInfo = findSingleNavigationLineMarker(fileName, markerFilter)
    val navigationHandler = lineMarkerInfo.getNavigationHandler
    assertNotNull(s"Expected non-null gutter navigation handler for file $fileName", navigationHandler)

    navigationHandler.navigate(createSimpleClickEvent(), lineMarkerInfo.getElement)
    PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()

    val selectedEditor = FileEditorManager.getInstance(project).getSelectedTextEditor
    assertNotNull(s"Expected selected editor after gutter navigation for file $fileName", selectedEditor)

    val selectedPsiFile = PsiDocumentManager.getInstance(project).getPsiFile(selectedEditor.getDocument)
    assertNotNull(s"Expected selected PSI file after gutter navigation for file $fileName", selectedPsiFile)

    val targetAtCaret = Option(TargetElementUtil.findTargetElement(selectedEditor, TargetElementUtil.getInstance.getAllAccepted))
      .orElse {
        val maybeElement = Option(selectedPsiFile.findElementAt(selectedEditor.getCaretModel.getOffset))
        maybeElement.flatMap(element => Option(PsiTreeUtil.getParentOfType(element, classOf[ScExportStmt])))
      }
      .orNull
    assertNotNull(
      s"Expected target element at caret after gutter navigation for file $fileName. Selected file: ${NavigationElementUtils.elementLocationPath(selectedPsiFile)}",
      targetAtCaret
    )

    targetAtCaret
  }

  private def findSingleNavigationLineMarker(
    fileName: String,
    markerFilter: LineMarkerInfo[_] => Boolean
  ): LineMarkerInfo[PsiElement] = {
    val allLineMarkersAtCaret = originalFixture.findGuttersAtCaret().asScala.toSeq.collect {
      case renderer: LineMarkerGutterIconRenderer[_] =>
        renderer.getLineMarkerInfo.asInstanceOf[LineMarkerInfo[PsiElement]]
    }

    val matchingLineMarkers = allLineMarkersAtCaret.filter(markerFilter)

    if (matchingLineMarkers.isEmpty) {
      fail(
        s"""Expected exactly one matching line marker at caret, got none for file $fileName.
           |All markers at caret:
           |${lineMarkersPresentation(allLineMarkersAtCaret)}
           |""".stripMargin
      )
    }
    if (matchingLineMarkers.size > 1) {
      fail(
        s"""Expected exactly one matching line marker at caret, got ${matchingLineMarkers.size} for file $fileName.
           |Matched markers:
           |${lineMarkersPresentation(matchingLineMarkers)}
           |All markers at caret:
           |${lineMarkersPresentation(allLineMarkersAtCaret)}
           |""".stripMargin
      )
    }

    matchingLineMarkers.head
  }

  private def isSuperNavigationLineMarker(lineMarkerInfo: LineMarkerInfo[_]): Boolean =
    lineMarkerInfo.getIcon == AllIcons.Gutter.OverridingMethod ||
      lineMarkerInfo.getIcon == AllIcons.Gutter.ImplementingMethod

  private def isImplementationNavigationLineMarker(lineMarkerInfo: LineMarkerInfo[_]): Boolean =
    lineMarkerInfo.getIcon == AllIcons.Gutter.OverridenMethod ||
      lineMarkerInfo.getIcon == AllIcons.Gutter.ImplementedMethod

  private def lineMarkersPresentation(lineMarkers: Seq[LineMarkerInfo[PsiElement]]): String = {
    val text = if (lineMarkers.isEmpty) "<none>" else lineMarkers.map(LineMarkerInfoPresentationUtils.describeLineMarkerWithRange).mkString("\n")
    TestIndentUtils.addIndentToAllLines(text, 2)
  }

  private def createSimpleClickEvent(): MouseEvent =
    new MouseEvent(new JLabel(), MouseEvent.MOUSE_CLICKED, 0L, 0, 0, 0, 1, false, 0)
}
