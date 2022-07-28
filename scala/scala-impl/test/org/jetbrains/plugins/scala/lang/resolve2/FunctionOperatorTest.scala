package org.jetbrains.plugins.scala.lang.resolve2

class FunctionOperatorTest extends ResolveTestBase {
  override def folderPath: String = {
    super.folderPath + "function/operator/"
  }

  def testDot(): Unit = doTest()
  def testDotAndParentheses(): Unit = doTest()
  def testNameArbitrary(): Unit = doTest()
  def testNameLong(): Unit = doTest()
  //TODO
//  def testParametersEmpty = doTest
  //TODO
//  def testParametersNone = doTest
  def testParametersTwo(): Unit = doTest()
  def testParametersType(): Unit = doTest()
  def testParentheses(): Unit = doTest()
  def testQualifierInstance(): Unit = doTest()
  //TODO
//  def testQualifierNone = doTest
  def testQualifierObject(): Unit = doTest()
  //TODO
//  def testQualifierThis = doTest
}