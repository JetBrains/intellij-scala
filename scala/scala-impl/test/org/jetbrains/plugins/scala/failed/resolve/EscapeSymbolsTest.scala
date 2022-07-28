package org.jetbrains.plugins.scala.failed.resolve

class EscapeSymbolsTest extends FailableResolveTest("escapeSymbols") {
  def testSCL5375(): Unit = doTest()
  def testSCL10630(): Unit = doTest()
}
