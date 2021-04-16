package org.jetbrains.plugins.scala.failed.resolve

/**
  * Created by Anton Yalyshev on 13/04/16.
  */
class EscapeSymbolsTest extends FailableResolveTest("escapeSymbols") {
  def testSCL5375(): Unit = doTest()
  def testSCL10630(): Unit = doTest()
}
