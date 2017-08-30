package org.jetbrains.plugins.scala.failed.resolve

import org.jetbrains.plugins.scala.PerfCycleTests
import org.jetbrains.plugins.scala.lang.psi.api.base.ScReferenceElement
import org.junit.Assert._
import org.junit.experimental.categories.Category

/**
  * Created by kate on 3/30/16.
  */

@Category(Array(classOf[PerfCycleTests]))
class MissingParamterClass extends FailedResolveTest("missingParameter") {
  def testSCL8967(): Unit = doTest()

  def testSCL12120(): Unit = doTest()

  def testSCL9719(): Unit = findReferenceAtCaret() match {
    case ref: ScReferenceElement =>
      val variants = ref.multiResolve(false)
      assertTrue(s"Single valid resolve expected, was: ${variants.length}", variants.length == 1 && variants.head.isValidResult)
  }
}
