package org.jetbrains.plugins.scala.failed.resolve

/**
  * @author Roman.Shein
  * @since 30.03.2016.
  */
class ProjectionTypeTest extends FailableResolveTest("projectionType") {
  def testSCL9789(): Unit = doTest()
}
