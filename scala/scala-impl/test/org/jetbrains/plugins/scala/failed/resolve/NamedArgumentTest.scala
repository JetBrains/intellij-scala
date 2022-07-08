package org.jetbrains.plugins.scala.failed.resolve

class NamedArgumentTest extends FailableResolveTest("namedArgument") {

  def testSCL10487(): Unit = doTest() //apply method

  def testSCL13063(): Unit = doTest() //apply method

}
