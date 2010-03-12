package org.jetbrains.plugins.scala.lang.resolve2


/**
 * Pavel.Fatin, 02.02.2010
 */

class FunctionAssignmentTest extends ResolveTestBase {
  override def getTestDataPath: String = {
    super.getTestDataPath + "function/assignment/"
  }

  //TODO
//  def testDotAndParentheses = doTest
  def testIncompatibleReturnType = doTest
  //TODO
//  def testLetter = doTest
  //TODO
//  def testLetters = doTest
  def testOpChar = doTest
  def testOpChars = doTest
  def testParentheses = doTest
  def testTwoArguments = doTest
  //TODO
//  def testValue = doTest
}