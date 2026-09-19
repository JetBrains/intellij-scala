package org.jetbrains.plugins.scala.lang.refactoring.changeSignature

import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.EditorTestUtil.{CARET_TAG => Caret}
import com.intellij.ui.UiInterceptors
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestCase
import org.junit.Assert

class ScalaChangeSignatureTest extends ScalaLightCodeInsightFixtureTestCase {
  def testGenerics(): Unit = {
    val before =
      s"""class MyObject[A] {
         |
         |  def ${Caret}foo[B](a: A, b: B): B = ???
         |}
         |""".stripMargin
    val after =
      s"""class MyObject[A] {
         |
         |  def foo[B](b1: B, b2: B): A = ???
         |}""".stripMargin

    doTest(before, after, Seq("b1" -> "B", "b2" -> "B"), "A")
  }

  def testHigherKindedTypes(): Unit = {
    val before =
      s"""class MyObject {
         |
         |  def ${Caret}foo[F[_]](a: F[Int]): F[Int] = ???
         |}
         |""".stripMargin
    val after =
      s"""class MyObject {
         |
         |  def foo[F[_]](b: F[String]): F[Unit] = ???
         |}""".stripMargin

    doTest(before, after, Seq("b" -> "F[String]"), "F[Unit]")
  }

  def testComplexHigherKindedTypes(): Unit = {
    val before =
      s"""class MyObject {
         |  type Mtl[F[_], A] = F[Option[A]]
         |
         |  def ${Caret}foo[F[_], G[_]](a: Mtl[F, Int]): Mtl[G, Int] = ???
         |}
         |""".stripMargin
    val after =
      s"""class MyObject {
         |  type Mtl[F[_], A] = F[Option[A]]
         |
         |  def foo[F[_], G[_]](a: Mtl[G, Int]): Mtl[F, Int] = ???
         |}""".stripMargin

    doTest(before, after, Seq("a" -> "Mtl[G, Int]"), "Mtl[F, Int]")
  }

  private def doTest(initialText: String, expectedText: String, parameters: Seq[(String, String)], returnType: String): Unit = {
    doRefactoringAction(initialText, parameters, returnType)
    Assert.assertEquals(expectedText, getFile.getText)
  }

  private def doRefactoringAction(fileText: String, parameters: Seq[(String, String)], returnType: String): Unit = {
    scalaFixture.configureFromFileText(fileText)

    UiInterceptors.register(new UiInterceptors.UiInterceptor[ScalaChangeSignatureDialog](classOf[ScalaChangeSignatureDialog]) {
      override protected def doIntercept(dialog: ScalaChangeSignatureDialog): Unit = {
        Disposer.register(getTestRootDisposable, dialog.getDisposable)
        dialog.setReturnType(returnType)
        parameters.zipWithIndex.foreach { case (name, typeText) -> idx =>
          dialog.setParameter(idx, name, typeText)
        }
        dialog.performOKAction()
      }
    })

    invokeChangeSignatureDialog()
  }

  private def invokeChangeSignatureDialog(): Unit = {
    new ScalaChangeSignatureHandler().invoke(getProject, getEditor, getFile, DataContext.EMPTY_CONTEXT)
  }
}
