package org.jetbrains.plugins.scala.lang.resolve

import org.jetbrains.plugins.scala.failed.resolve.FailableResolveTest

class ImplicitConversionTest extends FailableResolveTest("implicitConversion") {
  override protected def shouldPass = true

  def testSCL10447(): Unit = doTest()

  def testSCL12098(): Unit = doTest()

  def testSCL13306(): Unit = doTest()

  def testSCL13859(): Unit = doTest()
}
