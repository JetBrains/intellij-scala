package org.jetbrains.plugins.scala.lang.resolve2

class FunctionCountClashTest extends ResolveTestBase {
  override def folderPath: String = {
    super.folderPath + "function/count/clash/"
  }

//  def testEmptyAndNone = doTest
  def testOneAndEmpty(): Unit = doTest()
  def testOneAndNone(): Unit = doTest()
  def testOneAndTwo(): Unit = doTest()
}