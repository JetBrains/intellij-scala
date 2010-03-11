package org.jetbrains.plugins.scala.lang.resolve2


/**
 * Pavel.Fatin, 02.02.2010
 */

class FunctionOperatorTest extends ResolveTestBase {
  override def getTestDataPath: String = {
    super.getTestDataPath + "function/operator/"
  }

  def testDot = doTest
  def testDotAndParentheses = doTest
  def testNameArbitrary = doTest
  def testNameLong = doTest
  //TODO
//  def testParametersEmpty = doTest
  def testParametersNone = doTest
  def testParametersTwo = doTest
  def testParametersType = doTest
  def testParentheses = doTest
  def testQualifierInstance = doTest
  //TODO
//  def testQualifierNone = doTest
  def testQualifierObject = doTest
  //TODO
//  def testQualifierThis = doTest
}