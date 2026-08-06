package org.jetbrains.plugins.scala.failed.resolve

class OverloadedResolutionTest extends FailableResolveTest("overloadedResolution") {
  def testSCL2911(): Unit = doTest()

  def testSCL12052(): Unit = doTest()

  def testSCL9892(): Unit = doTest()
}
