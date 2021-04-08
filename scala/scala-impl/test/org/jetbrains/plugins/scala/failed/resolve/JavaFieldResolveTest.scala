package org.jetbrains.plugins.scala.failed.resolve

/**
  * @author Nikolay.Tropin
  */
class JavaFieldResolveTest extends FailedResolveTest("javaField") {
  def testScl6925(): Unit = doTest()
  def testScl12413(): Unit = doTest()
  def testScl12630(): Unit = doTest()
}
