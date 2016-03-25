package org.jetbrains.plugins.scala.failed.resolve

import org.intellij.lang.annotations.Language
import org.jetbrains.plugins.scala.PerfCycleTests
import org.jetbrains.plugins.scala.annotator.{AnnotatorHolderMock, ApplicationAnnotator}
import org.jetbrains.plugins.scala.base.SimpleTestCase
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScReferenceExpression
import org.junit.experimental.categories.Category

/**
  * @author Roman.Shein
  * @since 25.03.2016.
  */
@Category(Array(classOf[PerfCycleTests]))
class ArgumentTypeMismatchTest extends SimpleTestCase {
  def testSCL4687() = {
    val code =
      """
        |object A {
        |  class Z[T] {
        |    def m(t: T): T = t
        |  }
        |
        |  def foo[T]: Z[T] = null.asInstanceOf[Z[T]]
        |
        |  def goo[G](z: Z[G]): Z[G] = z
        |
        |  goo(foo).m(1)
        |}
      """.stripMargin
    assert(messages(code).isEmpty)
  }

  def messages(@Language(value = "Scala") code: String) = {
    val annotator = new ApplicationAnnotator {}
    val mock = new AnnotatorHolderMock

    val refExpr = code.parse.depthFirst.find(elem => elem.isInstanceOf[ScReferenceExpression] &&
      elem.getText == "goo(foo).m").get.asInstanceOf[ScReferenceExpression]
    annotator.annotateReference(refExpr, mock)
    mock.annotations
  }
}
