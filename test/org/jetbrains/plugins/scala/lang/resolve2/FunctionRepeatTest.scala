package org.jetbrains.plugins.scala.lang.resolve2


/**
 * Pavel.Fatin, 02.02.2010
 */

class FunctionRepeatTest extends ResolveTestBase {
  override def getTestDataPath: String = {
    super.getTestDataPath + "function/repeat/"
  }

  def testArray = doTest
  //TODO
//  def testArrayRaw = doTest
  //TODO
//  def testEmpty = doTest
  def testNone = doTest
  def testOne = doTest
  def testTwo = doTest

  //TOD
//  def testIncompatibleArray = doTest
  //TODO
//  def testIncompatibleOne = doTest
  //TODO
//  def testIncompatibleTwo = doTest
}