package org.jetbrains.plugins.scala.lang.resolve2


/**
 * Pavel.Fatin, 02.02.2010
 */

class FunctionBlockTest extends ResolveTestBase {
  override def folderPath: String = {
    super.folderPath + "function/block/"
  }

  def testBlocksToCurryied() = doTest()
  def testBlocksToTwo() = doTest()
  def testBlockToEmpty() = doTest()
  def testBlockToFunctionInt() = doTest()
  def testBlockToFunctionUnit() = doTest()
  def testBlockToFunctionWithArgs() = doTest()
  def testBlockToOtherType() = doTest()
  def testBlockToNone() = doTest()
  def testBlockToOne() = doTest()
  def testBlockToTwo() = doTest()
}