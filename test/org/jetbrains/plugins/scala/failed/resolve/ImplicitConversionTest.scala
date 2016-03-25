package org.jetbrains.plugins.scala.failed.resolve

import org.jetbrains.plugins.scala.PerfCycleTests
import org.junit.experimental.categories.Category

/**
  * @author Nikolay.Tropin
  */
@Category(Array(classOf[PerfCycleTests]))
class ImplicitConversionTest extends FailedResolveTest("implicitConversion") {
  def testScl8709(): Unit = doTest()

  def testScl9527(): Unit = doTest()

}
