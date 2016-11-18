package org.jetbrains.plugins.scala.failed.resolve

import org.jetbrains.plugins.scala.PerfCycleTests
import org.junit.experimental.categories.Category

/**
  * Created by kate on 3/29/16.
  */

@Category(Array(classOf[PerfCycleTests]))
class ScalazTest extends FailedResolveTest("scalaz"){
  override protected def additionalLibraries(): Array[String] = Array("scalaz")

  def testSCL7213(): Unit = doTest()

  def testSCL7227(): Unit = doTest()
}
