package org.jetbrains.plugins.scala.lang.resolve2



/**
 * Pavel.Fatin, 02.02.2010
 */

class ElementClashTest extends ResolveTestBase {
  override def getTestDataPath: String = {
    super.getTestDataPath + "element/clash/"
  }

  //TODO
//  def testClass = doTest
  //TODO
//  def testCaseClass = doTest
  def testClassParameter = doTest
  def testClassParameterValue = doTest
  def testClassParameterVariable = doTest
  def testCaseObject = doTest
  def testCaseClauseBinding = doTest
  def testCaseClauseNamed = doTest
  //TODO
//  def testTrait = doTest
  def testObject = doTest
  def testFunctionDefinition = doTest
  def testFunctionExpressionParameter = doTest
  def testFunctionParameter = doTest
  def testFunctionParameterClause = doTest
  def testConstructorParameter = doTest
  def testStatementForAssignment = doTest
  def testStatementForBinding = doTest
  def testStatementForValues = doTest
  //TODO
//  def testTypeAlias = doTest
  def testValue = doTest
  def testVariable = doTest
  //TODO
//  def testTypeParameterClass = doTest
  //TODO
//  def testTypeParameterFunction = doTest
  //TODO
//  def testTypeParameterTrait = doTest
}