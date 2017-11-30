package org.jetbrains.plugins.scala.failed.resolve

import org.jetbrains.plugins.scala.PerfCycleTests
import org.junit.experimental.categories.Category

/**
  * @author Nikolay.Tropin
  */
@Category(Array(classOf[PerfCycleTests]))
class JavaFieldResolveTest extends FailedResolveTest("javaField") {
  def testScl6925() = doTest()
  def testScl12413() = doTest()
  def testScl12630() = doTest()
}
