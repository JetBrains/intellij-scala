package org.jetbrains.plugins.scala.lang.resolve2

class FunctionRepeatTest extends ResolveTestBase {
  override def folderPath: String = {
    super.folderPath + "function/repeat/"
  }

  def testArraya(): Unit = doTest()
  def testArrayRaw(): Unit = doTest()
  def testEmpty(): Unit = doTest()
  def testNone(): Unit = doTest()
  def testOne(): Unit = doTest()
  def testTwo(): Unit = doTest()
  def testIncompatibleArraya(): Unit = doTest()
  def testIncompatibleOne(): Unit = doTest()
  def testIncompatibleTwo(): Unit = doTest()
}