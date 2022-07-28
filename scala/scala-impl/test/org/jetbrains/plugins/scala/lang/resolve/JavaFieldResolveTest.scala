package org.jetbrains.plugins.scala.lang.resolve

class JavaFieldResolveTest extends SimpleResolveTest("javaField") {
  def testScl10176(): Unit = doTest()
  def testScl13747(): Unit = doTest()
}
