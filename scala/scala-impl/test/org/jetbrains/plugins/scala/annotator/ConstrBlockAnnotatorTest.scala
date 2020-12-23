package org.jetbrains.plugins.scala.annotator

import org.intellij.lang.annotations.Language
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.annotator.element.ScConstrBlockAnnotator
import org.jetbrains.plugins.scala.base.SimpleTestCase
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScConstrBlock

class ConstrBlockAnnotatorTest extends SimpleTestCase {

  def test_wrong_order_auxiliary_constructors(): Unit = {
    val code =
      """
        |class Test {
        |  def this() = {
        |    println()
        |  }
        |}
      """.stripMargin
    assertMessages(messages(code))(
      Error("println()", ScalaBundle.message("constructor.invocation.expected"))
    )
  }


  def messages(@Language(value = "Scala") code: String): List[Message] = {
    val file: ScalaFile = code.parse

    implicit val mock: AnnotatorHolderMock = new AnnotatorHolderMock(file)

    file.depthFirst().filterByType[ScConstrBlock].foreach { constr =>
      ScConstrBlockAnnotator.annotate(constr, typeAware = true)
    }

    mock.annotations
  }
}
