package org.jetbrains.plugins.scala.lang.resolve

import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunctionDefinition
import org.jetbrains.plugins.scala.lang.psi.types.TypePresentationContext
import org.junit.Assert

class ExpectedTypeDrivenOverloadingResolutionTest extends SimpleResolveTestBase {

  import SimpleResolveTestBase._

  def testSCL16251(): Unit = {
    val (src, _) = setupResolveTest(
      None,
      s"""
         |val xs: Array[BigInt] = Arr${REFSRC}ay(1, 2, 3)
         |""".stripMargin -> "Test.scala"
    )

    val result = src.resolve()
    result match {
      case fn: ScFunctionDefinition =>
        fn.`type`()
          .foreach(tpe => Assert.assertEquals("T => ClassTag[T] => Array[T]", tpe.presentableText(TypePresentationContext.emptyContext)))
      case _ => Assert.fail("Invalid resolve result.")
    }
  }
}
