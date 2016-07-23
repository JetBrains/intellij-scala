package org.jetbrains.plugins.scala.lang.resolve

/**
  * @author Nikolay.Tropin
  */
class JavaFieldResolveTest extends SimpleResolveTest("javaField") {
  def testScl10176() = doTest()
}
