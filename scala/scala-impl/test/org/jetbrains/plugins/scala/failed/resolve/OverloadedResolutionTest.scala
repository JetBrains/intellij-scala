package org.jetbrains.plugins.scala.failed.resolve

import org.jetbrains.plugins.scala.PerfCycleTests
import org.junit.experimental.categories.Category

/**
  * Created by kate on 3/25/16.
  */

@Category(Array(classOf[PerfCycleTests]))
class OverloadedResolutionTest extends FailedResolveTest("overloadedResolution") {
  def testSCL2911(): Unit = doTest()

  def testSCL12052(): Unit = doTest()

  def testSCL9892(): Unit = doTest()
}
