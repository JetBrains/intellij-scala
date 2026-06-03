package org.jetbrains.plugins.scala.failed.annotator

import org.jetbrains.plugins.scala.annotator.AnnotatorTestBase
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTemplateDefinition

class IllegalInheritanceTest extends AnnotatorTestBase[ScTemplateDefinition] {
  override protected def shouldPass: Boolean = false

  def testSCL6979(): Unit = {
    assertNothing(
      messages(
        """
          |class Test{
          |  trait A {}
          |
          |  trait B {
          |    this: A =>
          |  }
          |
          |  def test(f : => Unit): Unit = {}
          |  this test new A with B { }
          |}
        """.stripMargin
      ))
  }
}
