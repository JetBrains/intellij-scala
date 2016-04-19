package org.jetbrains.plugins.scala.failed.resolve

import org.jetbrains.plugins.scala.PerfCycleTests
import org.junit.experimental.categories.Category

/**
  * Created by Anton Yalyshev
  */
@Category(Array(classOf[PerfCycleTests]))
class CollectionsResolveTest extends FailedResolveTest("collectionsResolve") {

  def testSCL7209(): Unit = doTest()
}
