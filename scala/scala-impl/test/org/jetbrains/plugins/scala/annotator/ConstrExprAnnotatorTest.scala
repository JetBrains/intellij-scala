package org.jetbrains.plugins.scala.annotator

import org.intellij.lang.annotations.Language
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.annotator.element.ScConstrExprAnnotator
import org.jetbrains.plugins.scala.base.SimpleTestCase
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScConstrExpr

class ConstrExprAnnotatorTest extends SimpleTestCase {

  def test_wrong_order_auxiliary_constructors(): Unit = {
    val code =
      """
        |class Test {
        |  def this() = ???
        |}
      """.stripMargin
    assertMessages(messages(code))(
      Error("???", ScalaBundle.message("constructor.invocation.expected"))
    )
  }


  def messages(@Language(value = "Scala") code: String): List[Message] = {
    val file: ScalaFile = code.parse

    implicit val mock: AnnotatorHolderMock = new AnnotatorHolderMock(file)

    file.depthFirst().filterByType[ScConstrExpr].foreach { constr =>
      ScConstrExprAnnotator.annotate(constr, typeAware = true)
    }

    mock.annotations
  }
}
