package org.jetbrains.plugins.scala.failed.resolve

class ProjectionTypeTest extends FailableResolveTest("projectionType") {
  def testSCL9789(): Unit = doTest()
}
