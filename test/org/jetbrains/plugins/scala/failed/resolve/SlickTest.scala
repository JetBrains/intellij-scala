package org.jetbrains.plugins.scala.failed.resolve

import org.jetbrains.plugins.scala.PerfCycleTests
import org.junit.experimental.categories.Category

/**
  * @author Nikolay.Tropin
  */
@Category(Array(classOf[PerfCycleTests]))
class SlickTest extends FailedResolveTest("slick") {
  override protected def additionalLibraries(): Array[String] = Array("slick")

  def testSCL8829() = doTest()
}
