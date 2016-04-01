package org.jetbrains.plugins.scala.failed.resolve

import org.jetbrains.plugins.scala.PerfCycleTests
import org.junit.experimental.categories.Category

/**
  * Created by kate on 4/1/16.
  */

@Category(Array(classOf[PerfCycleTests]))
class UndescoreWithAnonymousFunction extends FailedResolveTest("undescoreWithAnonymous"){
  def testSCL9896(): Unit = doTest()
}
