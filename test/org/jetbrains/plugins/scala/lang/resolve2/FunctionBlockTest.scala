package org.jetbrains.plugins.scala.lang.resolve2


/**
 * Pavel.Fatin, 02.02.2010
 */

class FunctionBlockTest extends ResolveTestBase {
  override def getTestDataPath: String = {
    super.getTestDataPath + "function/block/"
  }

  def testBlocksToCurryied = doTest
  //TODO
//  def testBlocksToTwo = doTest
  //TODO
//  def testBlockToEmpty = doTest
  //TODO
//  def testBlockToFunctionInt = doTest
  def testBlockToFunctionUnit = doTest
  //TODO
//  def testBlockToFunctionWithArgs = doTest
  //TODO
//  def testBlockToOtherType = doTest
  //TODO
//  def testBlockToNone = doTest
  def testBlockToOne = doTest
  //TODO
//  def testBlockToTwo = doTest
}