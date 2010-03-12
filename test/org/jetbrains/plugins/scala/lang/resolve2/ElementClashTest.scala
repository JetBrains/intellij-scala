package org.jetbrains.plugins.scala.lang.resolve2



/**
 * Pavel.Fatin, 02.02.2010
 */

class ElementClashTest extends ResolveTestBase {
  override def getTestDataPath: String = {
    super.getTestDataPath + "element/clash/"
  }

  def testClass = doTest
  def testCaseClass = doTest
  def testClassParameter = doTest
  def testClassParameterValue = doTest
  def testClassParameterVariable = doTest
  def testCaseObject = doTest
  def testCaseClauseBinding = doTest
  def testCaseClauseNamed = doTest
  def testTrait = doTest
  def testNamedParameter = doTest
  def testObject = doTest
  def testFunctionDefinition = doTest
  def testFunctionExpressionParameter = doTest
  def testFunctionParameter = doTest
  def testFunctionParameterClause = doTest
  def testConstructorParameter = doTest
  def testStatementForAssignment = doTest
  def testStatementForBinding = doTest
  def testStatementForValues = doTest
  def testTypeAlias = doTest
  def testValue = doTest
  def testVariable = doTest
  def testTypeParameterClass = doTest
  def testTypeParameterFunction = doTest
  def testTypeParameterTrait = doTest
}