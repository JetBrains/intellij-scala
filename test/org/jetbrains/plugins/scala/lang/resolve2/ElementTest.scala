package org.jetbrains.plugins.scala.lang.resolve2



/**
 * Pavel.Fatin, 02.02.2010
 */

class ElementTest extends ResolveTestBase {
  override def getTestDataPath: String = {
    super.getTestDataPath + "element/"
  }

  def testCaseClass = doTest
  def testClass = doTest
  def testObject = doTest
  def testTrait = doTest
  def testFunctionDefinition = doTest
  def testFunctionParameter = doTest
  def testFunctionParameterClause = doTest
  def testConstructorParameter = doTest
  def testTypeAlias = doTest
  def testValue = doTest
  def testVariable = doTest
  def testTypeParameter = doTest
  def testBinding = doTest
  def testValues = doTest
  def testStatementForValue = doTest
  def testStatementForValues = doTest
  def testStatementForBinding = doTest
  def testStatementForAssignment = doTest
  def testFunctionExpressionParameter = doTest
  def testCaseClauseParameter = doTest
  def testCaseClauseNamed = doTest
  def testCaseClauseBinding = doTest
}