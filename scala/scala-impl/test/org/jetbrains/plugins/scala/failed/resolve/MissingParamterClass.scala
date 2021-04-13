package org.jetbrains.plugins.scala.failed.resolve

/**
  * Created by kate on 3/30/16.
  */

class MissingParamterClass extends FailableResolveTest("missingParameter") {
  def testSCL8967(): Unit = doTest()

  def testSCL9719(): Unit = doTest()
}
