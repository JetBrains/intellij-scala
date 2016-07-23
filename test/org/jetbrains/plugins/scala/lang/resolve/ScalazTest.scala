package org.jetbrains.plugins.scala.lang.resolve

import org.jetbrains.plugins.scala.PerfCycleTests
import org.junit.experimental.categories.Category

/**
  * Created by kate on 3/29/16.
  */

class ScalazTest extends SimpleResolveTest("scalaz"){
  override protected def additionalLibraries(): Array[String] = Array("scalaz")

  def testSCL7213(): Unit = doTest()
}
