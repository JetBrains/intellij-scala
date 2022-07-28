package org.jetbrains.plugins.scala.lang.resolve2

class FunctionCountTest extends ResolveTestBase {
  override def folderPath: String = {
    super.folderPath + "function/count/"
  }

  def testEmptyToEmpty(): Unit = doTest()
  def testEmptyToNone(): Unit = doTest()
  def testEmptyToOne(): Unit = doTest()
  def testNoneToEmpty(): Unit = doTest()
  def testNoneToNone(): Unit = doTest()
  def testNoneToOne(): Unit = doTest()
  def testOneToEmpty(): Unit = doTest()
  def testOneToNone(): Unit = doTest()
  def testOneToOne(): Unit = doTest()
  def testOneToTwo(): Unit = doTest()
  def testTwoToOne(): Unit = doTest()
  def testTwoToTwo(): Unit = doTest()
  def testTupling(): Unit = doTest()
}