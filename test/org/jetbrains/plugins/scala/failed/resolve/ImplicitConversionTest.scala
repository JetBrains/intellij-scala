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

  def testScl4968(): Unit = doTest()
  
  def testScl7974(): Unit = doTest()

  def testSCL9224(): Unit = doTest()

  def testSCL8609(): Unit = doTest()

  def testSCL8643(): Unit = doTest()

  def testSCL7887(): Unit = doTest()

  def testSCL8757(): Unit = doTest()

  def testSCL10091(): Unit = doTest()

  def testSCL8660(): Unit = doTest()

  def testSCL8964(): Unit = doTest()
}
