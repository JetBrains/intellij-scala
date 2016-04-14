package org.jetbrains.plugins.scala.failed.resolve

import org.jetbrains.plugins.scala.PerfCycleTests
import org.junit.experimental.categories.Category

/**
  * @author Nikolay.Tropin
  */
@Category(Array(classOf[PerfCycleTests]))
class BacktickedResolveTest extends FailedResolveTest("backticked") {

  def testScl10165(): Unit = doTest()

}
