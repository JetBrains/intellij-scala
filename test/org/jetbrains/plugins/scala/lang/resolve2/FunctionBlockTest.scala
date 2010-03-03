package org.jetbrains.plugins.scala.lang.resolve2


/**
 * Pavel.Fatin, 02.02.2010
 */

class FunctionBlockTest extends ResolveTestBase {
  override def getTestDataPath: String = {
    super.getTestDataPath + "function/block/"
  }

  def testBlocksToCurryied = doTest
  def testBlocksToTwo = doTest
  def testBlockToEmpty = doTest
  def testBlockToFunctionInt = doTest
  //TODO
//  def testBlockToFunctionUnit = doTest
  //TODO
//  def testBlockToFunctionWithArgs = doTest
  def testBlockToOtherType = doTest
  def testBlockToNone = doTest
  def testBlockToOne = doTest
  def testBlockToTwo = doTest
}