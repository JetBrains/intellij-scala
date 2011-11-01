package org.jetbrains.plugins.scala.lang.resolve2


/**
 * Pavel.Fatin, 02.02.2010
 */

class FunctionAssignmentTest extends ResolveTestBase {
  override def folderPath: String = {
    super.folderPath + "function/assignment/"
  }

  def testClash = doTest
  def testClashWithType = doTest
  def testDotAndParentheses1 = doTest
  def testDotAndParentheses2 = doTest
  def testIllegalNames = doTest
  def testIllegalChars = doTest
  def testIncompatibleReturnType = doTest
  def testIncompatibleType = doTest
  def testInstance = doTest
  def testLegalOpChars = doTest
  def testNotAssignmentOrdinary = doTest
  def testPostfix = doTest
  def testOpChar = doTest
  def testOpChars = doTest
  def testParentheses = doTest
  def testStartsWithEqual = doTest
  def testTwoArguments = doTest
  def testValue = doTest
}