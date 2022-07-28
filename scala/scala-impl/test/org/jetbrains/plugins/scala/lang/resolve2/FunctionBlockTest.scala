package org.jetbrains.plugins.scala.lang.resolve2

class FunctionBlockTest extends ResolveTestBase {
  override def folderPath: String = {
    super.folderPath + "function/block/"
  }

  def testBlocksToCurryied(): Unit = doTest()
  def testBlocksToTwo(): Unit = doTest()
  def testBlockToEmpty(): Unit = doTest()
  def testBlockToFunctionInt(): Unit = doTest()
  def testBlockToFunctionUnit(): Unit = doTest()
  def testBlockToFunctionWithArgs(): Unit = doTest()
  def testBlockToOtherType(): Unit = doTest()
  def testBlockToNone(): Unit = doTest()
  def testBlockToOne(): Unit = doTest()
  def testBlockToTwo(): Unit = doTest()
}