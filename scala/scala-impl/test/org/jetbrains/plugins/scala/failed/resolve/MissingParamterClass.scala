package org.jetbrains.plugins.scala.failed.resolve

class MissingParamterClass extends FailableResolveTest("missingParameter") {
  def testSCL8967(): Unit = doTest()

  def testSCL9719(): Unit = doTest()
}
