package org.jetbrains.plugins.scala.lang.stubs

import org.jetbrains.plugins.scala.lang.psi.api.base.ScAnnotation
import org.jetbrains.plugins.scala.lang.psi.stubs.ScAnnotationStub
import org.junit.Assert

class ScAnnotationStubTest extends ScalaStubTestBase {
  def testAnnotationExpressionParent(): Unit = {
    val text =
      """
        |@deprecated("use 'Foo' instead", "1.2.3")
        |class Abc {
        |  @throws(classOf[Exception])
        |  def bar() = ???
        |}""".stripMargin

    doTest[ScAnnotationStub](text) { stub =>
      stub.annotationExpr match {
        case Some(expr) =>
          Assert.assertTrue("Annotation expression should always have PsiAnnotation as a parent",
            expr.getParent.isInstanceOf[ScAnnotation])
      }

    }
  }
}
