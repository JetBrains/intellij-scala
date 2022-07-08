package org.jetbrains.plugins.scala.lang.resolve

class EscapeSymbolsTest extends SimpleResolveTest("escapeSymbols") {
  def testSCL7704(): Unit = doTest()
  def testSCL10116(): Unit = doTest()
  def testSCL10662(): Unit = doTest()

}
