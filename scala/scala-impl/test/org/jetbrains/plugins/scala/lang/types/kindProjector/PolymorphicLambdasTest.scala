package org.jetbrains.plugins.scala.lang.types.kindProjector

import org.jetbrains.plugins.scala.lang.psi.api.expr.ScMethodCall
import org.jetbrains.plugins.scala.lang.types.utils.ScPsiElementAssertionTestBase

class PolymorphicLambdasTest extends ScPsiElementAssertionTestBase[ScMethodCall] with KindProjectorSetUp {
  override def folderPath = baseRootPath + "types/kindProjector/polymorphicLambdas/"

  override def computeRepresentation(t: ScMethodCall) =
    t.`type`() match {
      case Right(res) => Right(res.presentableText)
      case Left(f)    => Left(f.toString)
    }

  def testInfixApply(): Unit  = doTest()
  def testInfixMethod(): Unit = doTest()
  def testRegularType(): Unit = doTest()
}
