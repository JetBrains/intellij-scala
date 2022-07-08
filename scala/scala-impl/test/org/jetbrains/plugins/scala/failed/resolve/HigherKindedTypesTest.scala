package org.jetbrains.plugins.scala.failed.resolve

class HigherKindedTypesTest extends FailableResolveTest("higherKinded") {

  override protected def shouldPass: Boolean = false

  def testSCL12929(): Unit = doTest()
}
