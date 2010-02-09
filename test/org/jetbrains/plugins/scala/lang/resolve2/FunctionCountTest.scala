package org.jetbrains.plugins.scala.lang.resolve2


/**
 * Pavel.Fatin, 02.02.2010
 */

class FunctionCountTest extends ResolveTestBase {
  override def getTestDataPath: String = {
    super.getTestDataPath + "function/count/"
  }

  def testEmptyToEmpty = doTest
  def testEmptyToNone = doTest
  //TODO
//  def testEmptyToOne = doTest

  //TODO
//  def testNoneToEmpty = doTest
  def testNoneToNone = doTest
  //TODO
//  def testNoneToOne = doTest

  //TODO
//  def testOneToEmpty = doTest
  //TODO
//  def testOneToNone = doTest
  def testOneToOne = doTest
  //TODO
//  def testOneToTwo = doTest

  //TODO
//  def testTwoToOne = doTest
  def testTwoToTwo = doTest
}