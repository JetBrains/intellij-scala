package org.jetbrains.plugins.scala.lang.resolve2


/**
 * Pavel.Fatin, 02.02.2010
 */

class FunctionAssignmentTest extends ResolveTestBase {
  override def getTestDataPath: String = {
    super.getTestDataPath + "function/assignment/"
  }

  def testClash = doTest
  def testClashWithType = doTest
  //TODO
//  def testDotAndParentheses = doTest
  def testIllegalOpChar = doTest
  def testIncompatibleReturnType = doTest
  def testIncompatibleType = doTest
  def testInstance = doTest
  def testNotAssignmentOrdinary = doTest
  //TODO
//  def testNotAssignmentPostfix = doTest
  def testOpChar = doTest
  def testOpChars = doTest
  def testParentheses = doTest
  def testStartsWithEqual = doTest
  def testTwoArguments = doTest
  def testValue = doTest
}