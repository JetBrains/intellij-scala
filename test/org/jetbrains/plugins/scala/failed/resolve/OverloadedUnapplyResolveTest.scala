package org.jetbrains.plugins.scala.failed.resolve

import org.jetbrains.plugins.scala.PerfCycleTests
import org.jetbrains.plugins.scala.lang.psi.api.base.ScStableCodeReferenceElement
import org.junit.Assert._
import org.junit.experimental.categories.Category

/**
  * @author Nikolay.Tropin
  */
@Category(Array(classOf[PerfCycleTests]))
class OverloadedUnapplyResolveTest extends FailedResolveTest("overloadedUnapply") {

  def testScl9437_Unqualified(): Unit = {
    findReferenceAtCaret match {
      case st: ScStableCodeReferenceElement =>
        val variants = st.multiResolve(false)
        assertTrue(s"Single resolve expected, was: ${variants.length}", variants.length == 1)
    }
  }

  def testScl9437_Qualified(): Unit = {
    findReferenceAtCaret match {
      case st: ScStableCodeReferenceElement =>
        val variants = st.multiResolve(false)
        assertTrue(s"Single resolve expected, was: ${variants.length}", variants.length == 1)
    }
  }
}
