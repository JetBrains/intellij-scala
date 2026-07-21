package org.jetbrains.plugins.scala
package lang
package navigation

import com.intellij.codeInsight.navigation.GotoTargetHandler
import com.intellij.ide.util.PsiElementListCellRenderer
import com.intellij.platform.backend.presentation.TargetPresentation
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.CodeInsightTestUtil
import org.intellij.lang.annotations.Language

import org.jetbrains.plugins.scala.extensions.{ObjectExt, StringExt}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.ScExportStmt
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTypeDefinition
import org.jetbrains.plugins.scala.util.MarkersUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

class GoToImplementationTest_Scala3 extends GoToImplementationTestBase {

  override protected def supportedIn(version: ScalaVersion): Boolean = version.isScala3

  private def doTest(@Language("Scala 3") text: String): Unit = doGoToImplementationTest(text)

  def testExportedMemberTarget(): Unit = doTest(
    s"""trait Base {
       |  def run$CARET(): Unit
       |}
       |
       |trait Mixin {
       |  def run(): Unit = ()
       |}
       |
       |class Exported extends Base {
       |  val delegate: Mixin = new Mixin {}
       |  ${START}export delegate.run$END
       |}
       |""".stripMargin
  )

  def testOverloadedExportedMemberTarget(): Unit = doTest(
    s"""trait Base {
       |  def run$CARET(): Unit
       |}
       |
       |trait Mixin {
       |  def run(): Unit = ()
       |  def run(value: Int): Unit = ()
       |}
       |
       |class Exported extends Base {
       |  val delegate: Mixin = new Mixin {}
       |  ${START}export delegate.run$END
       |}
       |""".stripMargin
  )

  def testRenamedOverloadedExportedMemberTarget(): Unit = doTest(
    s"""trait Base {
       |  def execute$CARET(): Unit
       |}
       |
       |trait Mixin {
       |  def run(): Unit = ()
       |  def run(value: Int): Unit = ()
       |}
       |
       |class Exported extends Base {
       |  val delegate: Mixin = new Mixin {}
       |  ${START}export delegate.{run => execute}$END
       |}
       |""".stripMargin
  )

  def testMultipleExportedMemberTargetsForSameDeclaration(): Unit = doTest(
    s"""trait Base {
       |  def run$CARET(): Unit
       |}
       |
       |object Shared {
       |  def run(): Unit = ()
       |}
       |
       |class First extends Base {
       |  ${START}export Shared.run$END
       |}
       |
       |class Second extends Base {
       |  ${START}export Shared.run$END
       |}
       |""".stripMargin
  )

  def testHasImplementationsTargetPresentations(): Unit = {
    val text =
      s"""package example
         |
         |trait Base {
         |  def run$CARET(): Unit
         |}
         |
         |trait Mixin {
         |  def run(): Unit = ()
         |}
         |
         |class MethodImplementation extends Base {
         |  override def run(): Unit = ()
         |}
         |
         |class ExportedImplementation extends Base {
         |  val delegate: Mixin = new Mixin {}
         |  export delegate.run
         |}
         |""".stripMargin
    val (textWithoutMarkers, _) =
      MarkersUtils.extractMarker(text.withNormalizedSeparator.trim, START, END, caretMarker = Some(CARET))
    configureFromFileText(textWithoutMarkers)

    val gotoData = CodeInsightTestUtil.gotoImplementation(getEditor, getFile)
    assertEquals(2, gotoData.targets.length)

    val method = gotoData.targets.collectFirst { case function: ScFunction => function }.orNull
    val exportStmt = gotoData.targets.collectFirst { case statement: ScExportStmt => statement }.orNull
    assertTrue(method != null)
    assertTrue(exportStmt != null)
    assertEquals("export delegate.run", exportStmt.getText.trim)

    val methodContainingType = method.getContainingClass
    assertTrue(methodContainingType != null)

    val containingType = PsiTreeUtil.getParentOfType(exportStmt, classOf[ScTypeDefinition])
    assertTrue(containingType != null)

    val targetItems = GotoTargetHandler.computePresentationInBackground(
      getProject,
      Array(method, exportStmt),
      gotoData.hasDifferentNames
    )
    val methodPresentation = targetItems.get(0).getPresentation
    val exportPresentation = targetItems.get(1).getPresentation

    val moduleLocation = Option(PsiElementListCellRenderer.getModuleTextWithIcon(method))
    val moduleLocationText = moduleLocation.map(_.getText).orNull
    val moduleLocationIcon = moduleLocation.map(_.getIcon).orNull

    assertPresentation(methodPresentation, ExpectedPresentation(
      presentableText = "MethodImplementation",
      icon = methodContainingType.getIcon(0),
      containerText = "example",
      locationText = moduleLocationText,
      locationIcon = moduleLocationIcon
    ))
    assertPresentation(exportPresentation, ExpectedPresentation(
      presentableText = "ExportedImplementation",
      icon = containingType.getIcon(0),
      containerText = "example",
      locationText = moduleLocationText,
      locationIcon = moduleLocationIcon
    ))
  }

  private case class ExpectedPresentation(
    presentableText: String,
    icon: javax.swing.Icon,
    containerText: String,
    locationText: String,
    locationIcon: javax.swing.Icon
  )

  private def assertPresentation(
    presentation: TargetPresentation,
    expected: ExpectedPresentation
  ): Unit = {
    assertEquals(expected.presentableText, presentation.getPresentableText)
    assertEquals(expected.icon, presentation.getIcon)
    assertEquals(expected.containerText, presentation.getContainerText)
    assertEquals(expected.locationText, presentation.getLocationText)
    assertEquals(expected.locationIcon, presentation.getLocationIcon)
  }
}
