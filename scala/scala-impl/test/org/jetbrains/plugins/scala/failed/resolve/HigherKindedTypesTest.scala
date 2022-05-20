package org.jetbrains.plugins.scala.failed.resolve

/**
  * Created by Roman.Shein on 02.09.2016.
  */
class HigherKindedTypesTest extends FailableResolveTest("higherKinded") {

  override protected def shouldPass: Boolean = false

  def testSCL12929(): Unit = doTest()
}
