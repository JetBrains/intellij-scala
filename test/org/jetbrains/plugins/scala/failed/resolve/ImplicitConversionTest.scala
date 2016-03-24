package org.jetbrains.plugins.scala.failed.resolve

import org.jetbrains.plugins.scala.PerfCycleTests
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScReferenceExpression
import org.junit.Assert._
import org.junit.experimental.categories.Category

/**
  * @author Nikolay.Tropin
  */
@Category(Array(classOf[PerfCycleTests]))
class ImplicitConversionTest extends FailedResolveTest("implicitConversion") {
  def testScl8709(): Unit = {
    findReferenceAtCaret match {
      case ref: ScReferenceExpression =>
        val variants = ref.multiResolve(false)
        assertTrue(s"Single resolve expected, was: ${variants.length}", variants.length == 1)
    }
  }
}
