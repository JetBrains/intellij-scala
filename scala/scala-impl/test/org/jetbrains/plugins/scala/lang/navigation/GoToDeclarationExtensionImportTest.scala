package org.jetbrains.plugins.scala.lang.navigation

import com.intellij.codeInsight.navigation.actions.GotoDeclarationAction.findAllTargetElements
import com.intellij.codeInsight.navigation.impl.{GtdProvidersKt, NavigationActionResult}
import com.intellij.platform.backend.presentation.TargetPresentation
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.{LatestScalaVersions, ScalaVersion}
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.junit.Assert.{assertEquals, assertTrue}

import scala.jdk.CollectionConverters._

final class GoToDeclarationExtensionImportTest extends GoToDeclarationTestBase {

  override protected def supportedIn(version: ScalaVersion): Boolean =
    version >= LatestScalaVersions.Scala_3_0

  def testImportSelectorResolvesEveryExtensionOverload(): Unit = {
    configureSimpleExtensions()

    val targets = findAllTargetElements(getProject, getEditor, getEditor.getCaretModel.getOffset).toSeq

    assertEquals(3, targets.size)
    assertTrue(targets.forall(_.isInstanceOf[ScFunction]))
    val functions = targets.collect { case function: ScFunction => function }
    assertEquals(Set("present"), functions.map(_.name).toSet)
    assertTrue(functions.forall(_.isExtensionMethod))
  }

  def testImportSelectorPopupShowsEveryExtensionOverloadWithItsReceiver(): Unit = {
    configureSimpleExtensions()

    assertEquals(
      Set(
        "User.present(suffix: String)" -> "demo.extensions.Definitions",
        "Project.present(suffix: String)" -> "demo.extensions.Definitions",
        "Domain.present(suffix: String)" -> "demo.extensions.Definitions"
      ),
      popupTargetPresentations.map(presentationPair).toSet
    )
  }

  def testImportSelectorPopupShowsGenericExtensionSignatures(): Unit = {
    configureFromFileText(
      s"""package demo.extensions
         |
         |class Box[T]
         |class Bag[T]
         |trait Render[A]
         |
         |object Definitions:
         |  extension [T](target: Box[T])
         |    def present[U](value: U)(using render: Render[U]): String = ???
         |  extension [T](target: Bag[T])
         |    def present[U](value: U)(using render: Render[U]): String = ???
         |
         |object Usage:
         |  import Definitions.pre${CARET}sent
         |""".stripMargin
    )

    assertEquals(
      Set(
        "Box[T].present[U](value: U)(using render: Render[U])" -> "demo.extensions.Definitions",
        "Bag[T].present[U](value: U)(using render: Render[U])" -> "demo.extensions.Definitions"
      ),
      popupTargetPresentations.map(presentationPair).toSet
    )
  }

  def testOrdinaryFunctionPresentationRemainsUnchanged(): Unit = {
    configureFromFileText(
      """package demo.extensions
        |
        |object Definitions:
        |  def regular(suffix: String): String = ???
        |""".stripMargin
    )

    val function = PsiTreeUtil.findChildOfType(getFile, classOf[ScFunction])
    assertTrue(function != null)
    assertTrue(!function.isExtensionMethod)
    assertEquals("regular", function.getPresentation.getPresentableText)
    assertEquals("(demo.extensions.Definitions)", function.getPresentation.getLocationString)
  }

  private def configureSimpleExtensions(): Unit =
    configureFromFileText(
      s"""package demo.extensions
         |
         |class User
         |class Project
         |class Domain
         |
         |object Definitions:
         |  extension (target: User) def present(suffix: String): String = ???
         |  extension (target: Project) def present(suffix: String): String = ???
         |  extension (target: Domain) def present(suffix: String): String = ???
         |
         |object Usage:
         |  import Definitions.pre${CARET}sent
         |""".stripMargin
    )

  private def popupTargetPresentations: Seq[TargetPresentation] = {
    val editor = getEditor
    val data = GtdProvidersKt.fromGTDProviders(getProject, editor, editor.getCaretModel.getOffset)
    assertTrue("Go To Declaration must obtain Scala provider action data", data != null)

    val result = data.result()
    assertTrue("Go To Declaration must produce a multi-target result", result.isInstanceOf[NavigationActionResult.MultipleTargets])
    result.asInstanceOf[NavigationActionResult.MultipleTargets].getTargets.asScala.map(_.getPresentation).toSeq
  }

  private def presentationPair(presentation: TargetPresentation): (String, String) =
    presentation.getPresentableText -> presentation.getContainerText
}
