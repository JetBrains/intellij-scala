package org.jetbrains.plugins.scala.failed.resolve

class StructuralTypeTest extends FailableResolveTest("structuralType") {
  def testSCL6894(): Unit = doTest()
}
