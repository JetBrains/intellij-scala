package org.jetbrains.plugins.scala.failed.resolve

class MissingParamterClass extends FailableResolveTest("missingParameter") {
  def testSCL8967(): Unit = doTest()
}
