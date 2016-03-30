package org.jetbrains.plugins.scala.failed.resolve

import org.jetbrains.plugins.scala.PerfCycleTests
import org.junit.experimental.categories.Category

/**
  * @author Nikolay.Tropin
  */
@Category(Array(classOf[PerfCycleTests]))
class OverloadedUnapplyResolveTest extends FailedResolveTest("overloadedUnapply") {

  def testScl9437_Unqualified(): Unit = doTest()

  def testScl9437_Qualified(): Unit = doTest()

  def testSCL7279(): Unit = doTest()
}
