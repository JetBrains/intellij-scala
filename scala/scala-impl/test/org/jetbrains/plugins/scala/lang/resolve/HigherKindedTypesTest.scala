package org.jetbrains.plugins.scala.lang.resolve
import org.jetbrains.plugins.scala.failed.resolve.FailableResolveTest

class HigherKindedTypesTest extends FailableResolveTest("higherKinded") {
  override protected def shouldPass = true

  def testSCL12929(): Unit = doTest()
}
