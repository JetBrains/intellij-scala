package org.jetbrains.plugins.scala.failed.resolve

import org.jetbrains.plugins.scala.PerfCycleTests
import org.junit.experimental.categories.Category

/**
  * Created by Anton.Yalyshev on 29/04/16.
  */
@Category(Array(classOf[PerfCycleTests]))
class StringsResolveTest extends FailedResolveTest("strings") {

  def testSCL8414a(): Unit = doTest()

}
