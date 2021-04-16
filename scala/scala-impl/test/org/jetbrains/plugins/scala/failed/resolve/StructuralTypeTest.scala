package org.jetbrains.plugins.scala.failed.resolve

/**
  * @author Roman.Shein
  * @since 02.04.2016.
  */
class StructuralTypeTest extends FailableResolveTest("structuralType") {
  def testSCL6894(): Unit = doTest()
}
